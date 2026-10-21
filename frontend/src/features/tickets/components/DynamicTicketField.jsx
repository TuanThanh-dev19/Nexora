import { Form } from 'react-bootstrap'
import { orderedActive } from '../utils/createTicketContract'
import { EvidencePicker } from './EvidencePicker'
import { datetimeLocal } from '../../../shared/utils/datetimeLocal'

const RULE_LABELS = {
  minLength: 'Độ dài tối thiểu',
  maxLength: 'Độ dài tối đa',
  min: 'Giá trị sớm nhất / nhỏ nhất',
  max: 'Giá trị muộn nhất / lớn nhất',
  step: 'Bước số',
  minItems: 'Số lựa chọn tối thiểu',
  maxItems: 'Số lựa chọn tối đa',
  minFiles: 'Số ảnh tối thiểu',
  maxFiles: 'Số ảnh tối đa',
  maxSizeBytes: 'Dung lượng ảnh tối đa (byte)',
  allowedTypes: 'Định dạng ảnh cho phép',
  pattern: 'Định dạng yêu cầu',
}

export function DynamicTicketField({
  field,
  value,
  onChange,
  error,
  photos,
  onPhotos,
  disabled,
  errors,
}) {
  const id = `dynamic-${field.fieldKey}`
  const common = {
    id,
    disabled,
    'aria-describedby': `${id}-help ${id}-error`,
    'aria-invalid': !!error,
    isInvalid: !!error,
  }
  const rules = field.validationRules ?? {}
  let control
  if (field.fieldType === 'IMAGE')
    control = (
      <EvidencePicker
        field={field}
        photos={photos}
        onChange={onPhotos}
        disabled={disabled}
        errors={errors}
        error={error}
      />
    )
  else if (['SELECT', 'MULTI_SELECT', 'BOOLEAN'].includes(field.fieldType))
    control = (
      <Form.Select
        {...common}
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
          orderedActive(field.options).map((o) => (
            <option value={o.optionValue} key={o.id}>
              {o.optionLabel}
            </option>
          ))
        )}
      </Form.Select>
    )
  else
    control = (
      <Form.Control
        {...common}
        as={field.fieldType === 'TEXTAREA' ? 'textarea' : 'input'}
        rows={field.fieldType === 'TEXTAREA' ? 3 : undefined}
        type={
          { NUMBER: 'number', DATE: 'date', DATETIME: 'datetime-local' }[
            field.fieldType
          ] ?? 'text'
        }
        step={
          field.fieldType === 'NUMBER'
            ? (rules.step ?? 'any')
            : field.fieldType === 'DATETIME'
              ? 1
              : undefined
        }
        min={
          field.fieldType === 'DATETIME'
            ? datetimeLocal(rules.min)
            : ['NUMBER', 'DATE'].includes(field.fieldType)
              ? rules.min
              : undefined
        }
        max={
          field.fieldType === 'DATETIME'
            ? datetimeLocal(rules.max)
            : ['NUMBER', 'DATE'].includes(field.fieldType)
              ? rules.max
              : undefined
        }
        value={value ?? ''}
        placeholder={field.placeholder ?? ''}
        onChange={(e) => onChange(e.target.value)}
      />
    )
  return (
    <Form.Group className="mb-3">
      {field.fieldType !== 'IMAGE' && (
        <Form.Label htmlFor={id}>
          {field.label}
          {field.required && ' *'}
        </Form.Label>
      )}
      {control}
      <Form.Text id={`${id}-help`}>
        {field.fieldType !== 'IMAGE' && field.helpText}
        {Object.keys(rules).length > 0 && (
          <span className="d-block">
            Quy tắc:{' '}
            {Object.entries(rules)
              .map(
                ([k, v]) =>
                  `${RULE_LABELS[k] ?? k}: ${Array.isArray(v) ? v.join(', ') : v}`,
              )
              .join(' · ')}
          </span>
        )}
      </Form.Text>
      {error && field.fieldType !== 'IMAGE' && (
        <div id={`${id}-error`} role="alert" className="text-danger small">
          {error}
        </div>
      )}
    </Form.Group>
  )
}
