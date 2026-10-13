import { useRef } from 'react'
import { Button, Form } from 'react-bootstrap'
import { isSelect } from '../schemas/formValidation'

/** Controlled render-only component: no persistence or form-designer mutations. */
export function DynamicFormControl({
  id,
  field,
  rules,
  value,
  onChange,
  disabled = false,
}) {
  const fileInput = useRef(null)
  const props = {
    id,
    disabled,
    required: field.required,
    'aria-describedby': `${id}-help`,
    placeholder: field.placeholder,
  }
  if (isSelect(field.fieldType) || field.fieldType === 'BOOLEAN')
    return (
      <Form.Select
        {...props}
        multiple={field.fieldType === 'MULTI_SELECT'}
        value={value ?? (field.fieldType === 'MULTI_SELECT' ? [] : '')}
        onChange={(e) =>
          onChange(
            field.fieldType === 'MULTI_SELECT'
              ? [...e.target.selectedOptions].map((o) => o.value)
              : e.target.value,
          )
        }
      >
        {field.fieldType !== 'MULTI_SELECT' && (
          <option value="">Chọn giá trị</option>
        )}
        {field.fieldType === 'BOOLEAN' ? (
          <>
            <option value="true">Có</option>
            <option value="false">Không</option>
          </>
        ) : (
          field.options
            .filter((o) => o.active)
            .map((o, i) => (
              <option key={o.uiId ?? o.id ?? i} value={o.optionValue}>
                {o.optionLabel}
              </option>
            ))
        )}
      </Form.Select>
    )
  if (field.fieldType === 'IMAGE')
    return (
      <>
        <Form.Control
          {...props}
          ref={fileInput}
          className="visually-hidden"
          type="file"
          accept={rules.allowedTypes?.join(',') ?? 'image/jpeg,image/png'}
          multiple={(rules.maxFiles ?? 5) > 1}
          onChange={(e) =>
            onChange(
              [...e.target.files].map((f) => ({ name: f.name, size: f.size })),
            )
          }
        />
        <div className="d-flex flex-wrap align-items-center gap-2">
          <Button
            variant="outline-secondary"
            disabled={disabled}
            onClick={() => fileInput.current?.click()}
          >
            Chọn ảnh
          </Button>
          {!value?.length && (
            <span className="small text-secondary">Chưa chọn ảnh</span>
          )}
        </div>
        {value?.map((f, i) => (
          <p className="small" key={i}>
            {f.name} · {(f.size / 1024).toFixed(1)} KiB (xem trước)
          </p>
        ))}
      </>
    )
  return (
    <Form.Control
      {...props}
      as={field.fieldType === 'TEXTAREA' ? 'textarea' : 'input'}
      rows={field.fieldType === 'TEXTAREA' ? 3 : undefined}
      type={
        {
          TEXT: 'text',
          NUMBER: 'number',
          DATE: 'date',
          DATETIME: 'datetime-local',
        }[field.fieldType] ?? 'text'
      }
      value={value ?? ''}
      onChange={(e) => onChange(e.target.value)}
      min={rules.min}
      max={rules.max}
      step={
        field.fieldType === 'NUMBER'
          ? (rules.step ?? 'any')
          : field.fieldType === 'DATETIME'
            ? 'any'
            : undefined
      }
      minLength={rules.minLength}
      maxLength={rules.maxLength}
      pattern={rules.pattern}
    />
  )
}
