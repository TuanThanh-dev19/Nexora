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
          Trường {index + 1}: {field.label || 'Trường chưa đặt tên'}
        </strong>
        {!readOnly && (
          <div className="d-flex gap-2">
            <Button
              size="sm"
              variant="outline-secondary"
              aria-label={`Đưa trường ${index + 1} lên`}
              disabled={index === 0}
              onClick={() => onMove(-1)}
            >
              ↑
            </Button>
            <Button
              size="sm"
              variant="outline-secondary"
              aria-label={`Đưa trường ${index + 1} xuống`}
              disabled={index === count - 1}
              onClick={() => onMove(1)}
            >
              ↓
            </Button>
            <Button
              size="sm"
              variant="outline-danger"
              aria-label={`Xóa trường ${index + 1}`}
              onClick={onRemove}
            >
              Xóa
            </Button>
          </div>
        )}
      </Card.Header>
      <Card.Body>
        <fieldset disabled={readOnly}>
          <Row className="g-3">
            {input('fieldKey', 'Mã trường', { maxLength: 80 })}
            {input('label', 'Nhãn', { maxLength: 150 })}
            <Form.Group as={Col} md={6} controlId={`${field.uiId}-type`}>
              <Form.Label>Kiểu trường</Form.Label>
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
                label="Bắt buộc"
                checked={field.required}
                onChange={(e) => update('required', e.target.checked)}
              />
              <Form.Check
                id={`${field.uiId}-active`}
                label="Trường đang hoạt động"
                checked={field.active}
                onChange={(e) => update('active', e.target.checked)}
              />
            </Col>
            {input('helpText', 'Hướng dẫn', { maxLength: 500 })}
            {input('placeholder', 'Gợi ý nhập', { maxLength: 250 })}
            <Form.Group as={Col} xs={12} controlId={`${field.uiId}-rules`}>
              <Form.Label>Quy tắc kiểm tra (JSON)</Form.Label>
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
                Văn bản: minLength, maxLength, pattern. Số: min, max, step.
                Ngày/giờ: min, max. Chọn nhiều: minItems, maxItems. Ảnh:
                minFiles, maxFiles, maxSizeBytes, allowedTypes. DATETIME dùng
                ISO có múi giờ (Z hoặc +07:00). Ảnh chỉ JPEG/PNG, tối đa 5 ảnh
                và 5 MiB/ảnh.
              </Form.Text>
            </Form.Group>
          </Row>
          {(isSelect(field.fieldType) || field.options.length > 0) && (
            <section
              className="mt-4"
              aria-label={`Lựa chọn cho trường ${index + 1}`}
            >
              <h3 className="h6">Các lựa chọn</h3>
              {!isSelect(field.fieldType) && (
                <p className="text-danger">
                  Xóa các lựa chọn hiện có trước khi lưu kiểu trường này.
                </p>
              )}
              {field.options.length === 0 && (
                <p className="text-secondary">
                  Chưa có lựa chọn. Thêm ít nhất một lựa chọn đang hoạt động
                  trước khi phát hành.
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
                      <Form.Label>Giá trị lựa chọn {j + 1}</Form.Label>
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
                      <Form.Label>Nhãn lựa chọn {j + 1}</Form.Label>
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
                          label={`Lựa chọn hoạt động ${j + 1}`}
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
                              aria-label={`Đưa lựa chọn ${j + 1} lên`}
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
                              aria-label={`Đưa lựa chọn ${j + 1} xuống`}
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
                              aria-label={`Xóa lựa chọn ${j + 1}`}
                              onClick={() =>
                                update(
                                  'options',
                                  field.options.filter((_, k) => k !== j),
                                )
                              }
                            >
                              Xóa
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
                  Thêm lựa chọn
                </Button>
              )}
            </section>
          )}
        </fieldset>
      </Card.Body>
    </Card>
  )
}
