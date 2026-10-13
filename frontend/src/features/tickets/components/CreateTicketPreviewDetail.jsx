import { Alert, Card, Container } from 'react-bootstrap'
import { Link } from 'react-router'
import { TicketStatusBadge } from './TicketStatusBadge'
import { EvidenceThumbnail } from './EvidencePicker'

export function CreateTicketPreviewDetail({ ticket }) {
  if (!ticket)
    return (
      <Container className="page-container">
        <Alert variant="warning">
          Phiếu mô phỏng không còn trong bộ nhớ của phiên preview này. Không có
          Ticket nào được lưu trên máy chủ.
        </Alert>
        <Link to="/dev/create-ticket/new">Tạo phiếu mô phỏng khác</Link>
      </Container>
    )
  const valueLabel = (field, value) => {
    if (field?.fieldType === 'BOOLEAN') return value ? 'Có' : 'Không'
    if (Array.isArray(value))
      return value
        .map(
          (v) =>
            field?.options?.find((o) => o.optionValue === v)?.optionLabel ?? v,
        )
        .join(', ')
    return (
      field?.options?.find((o) => o.optionValue === value)?.optionLabel ??
      String(value)
    )
  }
  return (
    <Container className="page-container" style={{ maxWidth: 960 }}>
      <Link to="/dev/create-ticket/new" className="back-link">
        ← Tạo phiếu mô phỏng khác
      </Link>
      <div className="page-heading">
        <div>
          <p className="eyebrow">CHI TIẾT PHIẾU MÔ PHỎNG</p>
          <h1 className="h2">Phiếu mô phỏng {ticket.ticketNumber}</h1>
        </div>
        <TicketStatusBadge status={ticket.status} />
      </div>
      <Alert variant="success" role="status">
        Đã hoàn tất luồng gửi mô phỏng. Dữ liệu bên dưới chỉ tồn tại trong bộ
        nhớ preview; chưa được lưu vào máy chủ.
      </Alert>
      <Card className="mb-4">
        <Card.Body>
          <h2 className="h5">{ticket.title}</h2>
          <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
            {ticket.description}
          </p>
          <dl>
            <dt>Danh mục</dt>
            <dd>Thiết bị phòng học</dd>
            <dt>Biểu mẫu</dt>
            <dd>Phiên bản {ticket.formVersionNumber}</dd>
            <dt>Địa điểm</dt>
            <dd>{ticket.locationId === 1 ? 'Phòng A101' : 'Không chọn'}</dd>
            <dt>Tài sản</dt>
            <dd>{ticket.assetId === 1 ? 'Máy lạnh (AC-001)' : 'Không chọn'}</dd>
          </dl>
        </Card.Body>
      </Card>
      <Card className="mb-4">
        <Card.Body>
          <h2 className="h5">Nội dung biểu mẫu</h2>
          {ticket.dynamicValues.length ? (
            <dl>
              {ticket.dynamicValues.map(({ fieldKey, value }) => {
                const field = ticket.fields.find((f) => f.fieldKey === fieldKey)
                return (
                  <div key={fieldKey}>
                    <dt>{field?.label ?? fieldKey}</dt>
                    <dd
                      style={{
                        whiteSpace: 'pre-wrap',
                        overflowWrap: 'anywhere',
                      }}
                    >
                      {valueLabel(field, value)}
                    </dd>
                  </div>
                )
              })}
            </dl>
          ) : (
            <p>Không có dữ liệu bổ sung.</p>
          )}
        </Card.Body>
      </Card>
      <Card className="mb-4">
        <Card.Body>
          <h2 className="h5">Ảnh minh chứng ({ticket.files.length})</h2>
          <div className="d-flex flex-wrap gap-3">
            {ticket.attachments.map(({ fileIndex, fieldKey }) => {
              const file = ticket.files[fileIndex]
              return (
                <div
                  key={fileIndex}
                  className="border rounded p-3"
                  style={{ maxWidth: '100%', overflowWrap: 'anywhere' }}
                >
                  <EvidenceThumbnail file={file} />
                  <p className="mb-1">{file.name}</p>
                  <small>
                    {(file.size / 1024).toFixed(1)} KiB ·{' '}
                    {fieldKey
                      ? (ticket.fields.find((f) => f.fieldKey === fieldKey)
                          ?.label ?? fieldKey)
                      : 'Ảnh minh chứng chung'}
                  </small>
                </div>
              )
            })}
          </div>
        </Card.Body>
      </Card>
    </Container>
  )
}
