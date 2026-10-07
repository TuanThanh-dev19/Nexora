import { Card, Form } from 'react-bootstrap'
import { isSelect, parseRules, validateRules } from '../schemas/formValidation'

export function FormPreview({ fields }) {
  const active = fields.filter((f) => f.active)
  return (
    <Card>
      <Card.Header>
        <strong>Requester preview</strong>
      </Card.Header>
      <Card.Body>
        <p className="text-secondary small">
          Active fields and options in their current order. Save changes before
          publishing.
        </p>
        {!active.length && <p>No active fields.</p>}
        <fieldset disabled>
          {active.map((field) => {
            let rules = {}
            try {
              const parsed = parseRules(field.rulesText)
              if (!validateRules(field.fieldType, parsed)) rules = parsed
            } catch {
              /* The editor shows the JSON error. */
            }
            const props = {
              placeholder: field.placeholder,
              required: field.required,
            }
            let control
            if (isSelect(field.fieldType))
              control = (
                <Form.Select
                  {...props}
                  multiple={field.fieldType === 'MULTI_SELECT'}
                >
                  {field.options
                    .filter((o) => o.active)
                    .map((o) => (
                      <option key={o.uiId} value={o.optionValue}>
                        {o.optionLabel}
                      </option>
                    ))}
                </Form.Select>
              )
            else if (field.fieldType === 'BOOLEAN')
              control = <Form.Check label="Yes" />
            else if (field.fieldType === 'TEXTAREA')
              control = (
                <Form.Control
                  {...props}
                  as="textarea"
                  rows={3}
                  minLength={rules.minLength}
                  maxLength={rules.maxLength}
                />
              )
            else
              control = (
                <Form.Control
                  {...props}
                  type={
                    {
                      TEXT: 'text',
                      NUMBER: 'number',
                      DATE: 'date',
                      DATETIME: 'datetime-local',
                      IMAGE: 'file',
                    }[field.fieldType] ?? 'text'
                  }
                  min={rules.min}
                  max={rules.max}
                  step={rules.step}
                  minLength={rules.minLength}
                  maxLength={rules.maxLength}
                  pattern={rules.pattern}
                  accept={
                    field.fieldType === 'IMAGE'
                      ? (rules.allowedTypes?.join(',') ?? 'image/*')
                      : undefined
                  }
                  multiple={
                    field.fieldType === 'IMAGE' && (rules.maxFiles ?? 1) > 1
                  }
                />
              )
            return (
              <Form.Group key={field.uiId} className="mb-3">
                <Form.Label>
                  {field.label || 'Untitled field'}
                  {field.required && <span className="text-danger"> *</span>}
                </Form.Label>
                {control}
                {field.helpText && <Form.Text>{field.helpText}</Form.Text>}
              </Form.Group>
            )
          })}
        </fieldset>
      </Card.Body>
    </Card>
  )
}
