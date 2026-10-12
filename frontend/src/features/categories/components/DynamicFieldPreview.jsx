import { Badge, ListGroup } from 'react-bootstrap'
import { DYNAMIC_FIELD_TYPE_LABELS } from '../constants/dynamicFieldTypes'

export function DynamicFieldPreview({ field }) {
  return (
    <ListGroup.Item className="d-flex justify-content-between align-items-center">
      <span>
        {field.label}
        {field.required && <span className="text-danger ms-1">*</span>}
      </span>
      <Badge bg="light" text="dark">
        {DYNAMIC_FIELD_TYPE_LABELS[field.fieldType ?? field.type] ??
          field.fieldType ??
          field.type}
      </Badge>
    </ListGroup.Item>
  )
}
