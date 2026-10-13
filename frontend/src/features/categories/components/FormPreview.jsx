import { useState } from 'react'
import { Alert, Card, Form } from 'react-bootstrap'
import { parseRules, validateRules } from '../schemas/formValidation'
import { DynamicFormControl } from './DynamicFormControl'

export function FormPreview({ fields }) {
  const [values, setValues] = useState({})
  const active = fields.filter((f) => f.active)
  return (
    <Card id="category-preview">
      <Card.Header>
        <strong>Xem trước cho người gửi</strong>
      </Card.Header>
      <Card.Body>
        <p className="text-secondary small">
          Thử nhập dữ liệu mẫu tại đây. Dữ liệu xem trước không được lưu. Trường
          và lựa chọn theo thứ tự đang thiết kế.
        </p>
        {!active.length && <p>Không có trường đang hoạt động.</p>}
        {active.map((field, index) => {
          let rules, error
          try {
            rules = parseRules(field.rulesText)
            error = validateRules(field.fieldType, rules)
          } catch {
            error = 'JSON quy tắc không hợp lệ hoặc có khóa bị trùng.'
          }
          const key = `${field.uiId ?? field.id ?? index}-${field.fieldType}`
          const id = `preview-${key}`
          return (
            <Form.Group key={key} className="mb-3">
              <Form.Label htmlFor={id}>
                {field.label || 'Trường chưa đặt tên'}
                {field.required && <span className="text-danger"> *</span>}
              </Form.Label>
              {error ? (
                <Alert variant="warning" role="alert">
                  Không thể xem trước trường này: {error} Quy tắc gốc được giữ
                  nguyên.
                </Alert>
              ) : (
                <DynamicFormControl
                  id={id}
                  field={field}
                  rules={rules}
                  value={values[key]}
                  onChange={(value) =>
                    setValues((old) => ({ ...old, [key]: value }))
                  }
                />
              )}
              {field.helpText && (
                <Form.Text id={`${id}-help`}>{field.helpText}</Form.Text>
              )}
            </Form.Group>
          )
        })}
        <p className="small text-secondary">
          Ảnh mẫu mặc định dùng JPEG/PNG; quy tắc định dạng đã cấu hình được ưu
          tiên. Xem trước chỉ mô phỏng nhập liệu, không tải ảnh lên hoặc kiểm
          tra điều kiện gửi phiếu. Quy tắc đã lưu được giữ nguyên.
        </p>
      </Card.Body>
    </Card>
  )
}
