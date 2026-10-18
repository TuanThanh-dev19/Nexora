import { useState } from 'react'
import { Button, Card, ListGroup } from 'react-bootstrap'
import { TicketAttachmentPreview } from './TicketAttachmentPreview'
import { EmptyState } from '../../../shared/components/EmptyState'
import { TICKET_STATUS_LABELS } from '../constants/ticketStatuses'
import {
  ATTACHMENT_TYPE_LABELS,
  TICKET_ACTION_LABELS,
} from '../constants/ticketPresentation'
import { formatTicketDate, formatTicketTime } from '../utils/ticketFormatting'

function AttachmentList({ attachments }) {
  return (
    <ListGroup variant="flush" className="field-attachments">
      {attachments.map((attachment) => (
        <ListGroup.Item key={attachment.id}>
          <div className="fw-semibold text-break">
            {attachment.originalName}
          </div>
          <div className="text-secondary small">
            {ATTACHMENT_TYPE_LABELS[attachment.attachmentType] ??
              attachment.attachmentType}{' '}
            · {attachment.contentType} · {attachment.sizeBytes} byte
          </div>
          <div className="small">
            Người tải lên: {attachment.uploadedBy?.fullName ?? '—'} ·{' '}
            {formatTicketTime(attachment.createdAt)}
          </div>
        </ListGroup.Item>
      ))}
    </ListGroup>
  )
}

function DynamicAnswer({ field, answer, attachments }) {
  if (field.fieldType === 'IMAGE') {
    const linked = attachments.filter(
      (attachment) => String(attachment.fieldDefinitionId) === String(field.id),
    )
    return linked.length ? (
      <AttachmentList attachments={linked} />
    ) : (
      <span>Chưa có dữ liệu</span>
    )
  }
  if (!answer) return <span>Chưa có dữ liệu</span>
  const invalid = <span>Không đọc được giá trị</span>
  const raw = answer.valueJson
  // Keep the numeric JSON literal rather than rounding it through JS Number.
  if (field.fieldType === 'NUMBER') {
    return typeof raw === 'string' &&
      /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(raw.trim()) ? (
      <span>{raw.trim()}</span>
    ) : (
      invalid
    )
  }
  let value
  try {
    value = JSON.parse(raw)
  } catch {
    return invalid
  }
  if (value === null) return <span>Giá trị trống</span>
  const optionLabel = (code) =>
    field.options?.find((option) => option.value === code)?.label ?? code
  switch (field.fieldType) {
    case 'TEXT':
    case 'TEXTAREA':
      return typeof value === 'string' ? (
        <span className="text-break" style={{ whiteSpace: 'pre-wrap' }}>
          {value === '' ? 'Chuỗi rỗng' : value}
        </span>
      ) : (
        invalid
      )
    case 'BOOLEAN':
      return typeof value === 'boolean' ? (
        <span>{value ? 'Có' : 'Không'}</span>
      ) : (
        invalid
      )
    case 'SELECT':
      return typeof value === 'string' ? (
        <span>{optionLabel(value)}</span>
      ) : (
        invalid
      )
    case 'MULTI_SELECT':
      return Array.isArray(value) &&
        value.every((entry) => typeof entry === 'string') ? (
        value.length ? (
          <ul className="mb-0">
            {value.map((entry, index) => (
              <li key={`${index}-${entry}`}>{optionLabel(entry)}</li>
            ))}
          </ul>
        ) : (
          <span>Không có lựa chọn</span>
        )
      ) : (
        invalid
      )
    case 'DATE': {
      const date = formatTicketDate(value)
      return date ? <span>{date}</span> : invalid
    }
    case 'DATETIME':
      return typeof value === 'string' &&
        /^\d{4}-\d{2}-\d{2}T/.test(value) &&
        !Number.isNaN(new Date(value).getTime()) ? (
        <span>{formatTicketTime(value)}</span>
      ) : (
        invalid
      )
    default:
      return <span>Loại trường chưa được hỗ trợ</span>
  }
}

export function TicketDynamicValues({
  formVersion,
  dynamicValues,
  attachments,
}) {
  return (
    <Card className="ticket-section mb-3">
      <Card.Body>
        <h2 className="h5">Dữ liệu biểu mẫu</h2>
        {formVersion.fields.length === 0 ? (
          <EmptyState message="Biểu mẫu không có trường dữ liệu." />
        ) : (
          <dl className="mb-0 dynamic-values">
            {formVersion.fields.map((field) => (
              <div
                key={field.id}
                className="dynamic-field"
                data-field-type={field.fieldType}
              >
                <dt>{field.label}</dt>
                <dd className="mt-1 mb-0">
                  <DynamicAnswer
                    field={field}
                    answer={dynamicValues.find(
                      (value) =>
                        String(value.fieldDefinitionId) === String(field.id),
                    )}
                    attachments={attachments}
                  />
                </dd>
              </div>
            ))}
          </dl>
        )}
      </Card.Body>
    </Card>
  )
}

export function TicketStatusHistory({ history }) {
  return (
    <Card className="ticket-section mb-3">
      <Card.Body>
        <h2 className="h5">Lịch sử trạng thái</h2>
        {history.length === 0 ? (
          <EmptyState message="Chưa có lịch sử trạng thái." />
        ) : (
          <ol className="mb-0 ticket-timeline">
            {history.map((entry) => (
              <li key={entry.id}>
                <div className="fw-semibold">
                  {TICKET_ACTION_LABELS[entry.transitionAction] ??
                    entry.transitionAction}
                </div>
                <div>
                  {entry.fromStatus == null
                    ? 'Khởi tạo'
                    : (TICKET_STATUS_LABELS[entry.fromStatus] ??
                      entry.fromStatus)}{' '}
                  → {TICKET_STATUS_LABELS[entry.toStatus] ?? entry.toStatus}
                </div>
                <div className="small text-secondary">
                  {entry.changedBy?.fullName ?? '—'} ·{' '}
                  {formatTicketTime(entry.changedAt)}
                </div>
                {entry.reason && (
                  <p
                    className="mb-0 text-break"
                    style={{ whiteSpace: 'pre-wrap' }}
                  >
                    {entry.reason}
                  </p>
                )}
              </li>
            ))}
          </ol>
        )}
      </Card.Body>
    </Card>
  )
}

export function TicketAttachments({ ticketId, attachments }) {
  const [selected, setSelected] = useState(null)
  const available = Boolean(ticketId)
  return (
    <Card className="ticket-section mb-3">
      <Card.Body>
        <h2 className="h5">Tệp đính kèm</h2>
        <p className="text-secondary small">
          Mở ảnh để xem trước hoặc tải xuống bằng phiên đăng nhập hiện tại.
        </p>
        {attachments.length === 0 ? (
          <EmptyState message="Chưa có tệp đính kèm." />
        ) : (
          <div className="row g-3">
            {attachments.map((attachment) => (
              <div key={attachment.id} className="col-12 col-md-6 col-lg-4">
                <div className="border rounded p-3 h-100">
                  <h3 className="h6 text-break">{attachment.originalName}</h3>
                  <p className="small mb-1">
                    {ATTACHMENT_TYPE_LABELS[attachment.attachmentType] ??
                      attachment.attachmentType}{' '}
                    · {attachment.contentType} · {attachment.sizeBytes} byte
                  </p>
                  <p className="small text-secondary">
                    {attachment.uploadedBy?.fullName ?? '—'} ·{' '}
                    {formatTicketTime(attachment.createdAt)}
                  </p>
                  <Button
                    variant="outline-primary"
                    disabled={!available}
                    aria-label={`Xem ảnh ${attachment.originalName}`}
                    onClick={() => setSelected(attachment)}
                  >
                    Xem ảnh
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card.Body>
      {available && selected && (
        <TicketAttachmentPreview
          key={selected.id}
          ticketId={ticketId}
          attachment={selected}
          onHide={() => setSelected(null)}
        />
      )}
    </Card>
  )
}
