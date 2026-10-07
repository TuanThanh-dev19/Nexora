import { Button, Card, Col, Form, Row } from 'react-bootstrap'
import {
  DYNAMIC_FIELD_TYPES,
  DYNAMIC_FIELD_TYPE_LABELS,
} from '../constants/dynamicFieldTypes'
import { isSelect, moveItem, newOption } from '../schemas/formValidation'

export function FieldEditor({
  field,
  index,
  count,
  onChange,
  onMove,
  onRemove,
  errors = {},
  readOnly = false,
}) {
  const prefix = `fields[${index}]`
  const update = (key, value) => onChange({ ...field, [key]: value })
  const errorFor = (key) =>
    Object.entries(errors)
      .filter(
        ([path]) =>
          path === `${prefix}.${key}` || path.startsWith(`${prefix}.${key}.`),
      )
      .map(([, message]) => message)
      .join(' ')
  const input = (key, label, props = {}) => (
    <Form.Group as={Col} md={6} controlId={`${field.uiId}-${key}`}>
      <Form.Label>{label}</Form.Label>
      <Form.Control
        value={field[key] ?? ''}
        onChange={(e) => update(key, e.target.value)}
        isInvalid={!!errorFor(key)}
        {...props}
      />
      <Form.Control.Feedback type="invalid">
        {errorFor(key)}
      </Form.Control.Feedback>
    </Form.Group>
  )

  return (
    <Card className="mb-3">
      <Card.Header className="d-flex flex-wrap justify-content-between align-items-center gap-2">
        <strong>
          Field {index + 1}: {field.label || 'Untitled field'}
        </strong>
        {!readOnly && (
          <div className="d-flex gap-2">
            <Button
              size="sm"
              variant="outline-secondary"
              aria-label={`Move field ${index + 1} up`}
              disabled={index === 0}
              onClick={() => onMove(-1)}
            >
              ↑
            </Button>
            <Button
              size="sm"
              variant="outline-secondary"
              aria-label={`Move field ${index + 1} down`}
              disabled={index === count - 1}
              onClick={() => onMove(1)}
            >
              ↓
            </Button>
            <Button
              size="sm"
              variant="outline-danger"
              aria-label={`Remove field ${index + 1}`}
              onClick={onRemove}
            >
              Remove
            </Button>
          </div>
        )}
      </Card.Header>
      <Card.Body>
        <fieldset disabled={readOnly}>
          <Row className="g-3">
            {input('fieldKey', 'Field key', { maxLength: 80 })}
            {input('label', 'Label', { maxLength: 150 })}
            <Form.Group as={Col} md={6} controlId={`${field.uiId}-type`}>
              <Form.Label>Field type</Form.Label>
              <Form.Select
                value={field.fieldType}
                onChange={(e) =>
                  onChange({ ...field, fieldType: e.target.value })
                }
              >
                {DYNAMIC_FIELD_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {DYNAMIC_FIELD_TYPE_LABELS[type]}
                  </option>
                ))}
              </Form.Select>
            </Form.Group>
            <Col md={6} className="d-flex align-items-end gap-4 pb-2">
              <Form.Check
                id={`${field.uiId}-required`}
                label="Required"
                checked={field.required}
                onChange={(e) => update('required', e.target.checked)}
              />
              <Form.Check
                id={`${field.uiId}-active`}
                label="Active field"
                checked={field.active}
                onChange={(e) => update('active', e.target.checked)}
              />
            </Col>
            {input('helpText', 'Help text', { maxLength: 500 })}
            {input('placeholder', 'Placeholder', { maxLength: 250 })}
            <Form.Group as={Col} xs={12} controlId={`${field.uiId}-rules`}>
              <Form.Label>Validation rules (JSON)</Form.Label>
              <Form.Control
                as="textarea"
                rows={3}
                className="font-monospace"
                value={field.rulesText}
                onChange={(e) => update('rulesText', e.target.value)}
                isInvalid={!!errorFor('validationRules')}
              />
              <Form.Control.Feedback type="invalid">
                {errorFor('validationRules')}
              </Form.Control.Feedback>
              <Form.Text>
                Text: minLength, maxLength, pattern. Number: min, max, step.
                Date/time: min, max. Multi-select: minItems, maxItems. Image:
                minFiles, maxFiles, maxSizeBytes, allowedTypes.
              </Form.Text>
            </Form.Group>
          </Row>
          {(isSelect(field.fieldType) || field.options.length > 0) && (
            <section
              className="mt-4"
              aria-label={`Options for field ${index + 1}`}
            >
              <h3 className="h6">Options</h3>
              {!isSelect(field.fieldType) && (
                <p className="text-danger">
                  Remove the existing options before saving this field type.
                </p>
              )}
              {field.options.length === 0 && (
                <p className="text-secondary">
                  No options yet. Add an active option before publishing.
                </p>
              )}
              {field.options.map((option, j) => {
                const setOption = (key, value) =>
                  update(
                    'options',
                    field.options.map((o, k) =>
                      k === j ? { ...o, [key]: value } : o,
                    ),
                  )
                const op = `${prefix}.options[${j}]`
                return (
                  <Row className="g-2 mb-3 align-items-end" key={option.uiId}>
                    <Form.Group
                      as={Col}
                      md={4}
                      controlId={`${option.uiId}-value`}
                    >
                      <Form.Label>Option {j + 1} value</Form.Label>
                      <Form.Control
                        value={option.optionValue}
                        maxLength={100}
                        onChange={(e) =>
                          setOption('optionValue', e.target.value)
                        }
                        isInvalid={!!errors[`${op}.optionValue`]}
                      />
                      <Form.Control.Feedback type="invalid">
                        {errors[`${op}.optionValue`]}
                      </Form.Control.Feedback>
                    </Form.Group>
                    <Form.Group
                      as={Col}
                      md={4}
                      controlId={`${option.uiId}-label`}
                    >
                      <Form.Label>Option {j + 1} label</Form.Label>
                      <Form.Control
                        value={option.optionLabel}
                        maxLength={150}
                        onChange={(e) =>
                          setOption('optionLabel', e.target.value)
                        }
                        isInvalid={!!errors[`${op}.optionLabel`]}
                      />
                      <Form.Control.Feedback type="invalid">
                        {errors[`${op}.optionLabel`]}
                      </Form.Control.Feedback>
                    </Form.Group>
                    <Col md={4}>
                      <div className="d-flex flex-wrap align-items-center gap-2">
                        <Form.Check
                          id={`${option.uiId}-active`}
                          label={`Active option ${j + 1}`}
                          checked={option.active}
                          onChange={(e) =>
                            setOption('active', e.target.checked)
                          }
                        />
                        {!readOnly && (
                          <>
                            <Button
                              size="sm"
                              variant="outline-secondary"
                              aria-label={`Move option ${j + 1} up`}
                              disabled={j === 0}
                              onClick={() =>
                                update(
                                  'options',
                                  moveItem(field.options, j, -1),
                                )
                              }
                            >
                              ↑
                            </Button>
                            <Button
                              size="sm"
                              variant="outline-secondary"
                              aria-label={`Move option ${j + 1} down`}
                              disabled={j === field.options.length - 1}
                              onClick={() =>
                                update('options', moveItem(field.options, j, 1))
                              }
                            >
                              ↓
                            </Button>
                            <Button
                              size="sm"
                              variant="outline-danger"
                              aria-label={`Remove option ${j + 1}`}
                              onClick={() =>
                                update(
                                  'options',
                                  field.options.filter((_, k) => k !== j),
                                )
                              }
                            >
                              Remove
                            </Button>
                          </>
                        )}
                      </div>
                    </Col>
                  </Row>
                )
              })}
              {errorFor('options') && (
                <p className="text-danger">{errorFor('options')}</p>
              )}
              {!readOnly && isSelect(field.fieldType) && (
                <Button
                  size="sm"
                  variant="outline-primary"
                  disabled={field.options.length >= 200}
                  onClick={() =>
                    update('options', [...field.options, newOption()])
                  }
                >
                  Add option
                </Button>
              )}
            </section>
          )}
        </fieldset>
      </Card.Body>
    </Card>
  )
}
