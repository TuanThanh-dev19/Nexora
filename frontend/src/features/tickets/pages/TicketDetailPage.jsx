import { Alert, Badge, Card, Col, Container, Row, Stack } from 'react-bootstrap'
import { Link, useLocation, useParams } from 'react-router'
import { FiEye } from 'react-icons/fi'
import { PRIORITY_LABELS } from '../../../shared/constants/priorities'
import { isTicketWorkflowMockEnabled } from '../../../shared/config/env'
import { useAuth } from '../../auth/context/useAuth'
import { TicketActionPanel } from '../components/TicketActionPanel'
import { TechnicianWorkPanel } from '../components/TechnicianWorkPanel'
import { TicketComments } from '../components/TicketComments'
import { TicketFeedback } from '../components/TicketFeedback'
import { ticketViewerKey } from '../hooks/ticketQueryOptions'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { TicketErrorState } from '../components/TicketErrorState'
import { TicketStatusBadge } from '../components/TicketStatusBadge'
import {
  TicketAttachments,
  TicketDynamicValues,
  TicketStatusHistory,
} from '../components/TicketDetailSections'
import { FORM_STATUS_LABELS } from '../constants/ticketPresentation'
import { useTicket } from '../hooks/useTicket'
import { isPositiveId } from '../hooks/useTicketSearchParams'
import { formatTicketTime } from '../utils/ticketFormatting'

export function TicketDetailPage() {
  const { ticketId } = useParams()
  const location = useLocation()
  const { user } = useAuth()
  const query = useTicket(ticketId)
  const ticket = query.data
  const listSearch = location.state?.listSearch
  const backBase =
    location.state?.listFrom === 'my-work' ? '/my-work' : '/tickets'
  const back = `${backBase}${typeof listSearch === 'string' && listSearch.startsWith('?') ? listSearch : ''}`
  return (
    <Container className="page-container">
      {location.state?.createdTicketNumber && (
        <Alert variant="success" role="status">
          Đã tạo phiếu {location.state.createdTicketNumber}.
        </Alert>
      )}
      <Link to={back} className="back-link">
        ← {backBase === '/my-work' ? 'Công việc của tôi' : 'Danh sách phiếu'}
      </Link>
      <div className="page-heading">
        <div>
          <p className="eyebrow">THÔNG TIN SỰ CỐ</p>
          <h1 className="h2 mb-0">Chi tiết phiếu yêu cầu</h1>
        </div>
        <span className="read-only-label">
          <FiEye aria-hidden="true" />{' '}
          {isTicketWorkflowMockEnabled()
            ? 'Mô phỏng workflow'
            : 'Chi tiết phiếu'}
        </span>
      </div>
      {!isPositiveId(ticketId) ? (
        <TicketErrorState status={400} />
      ) : query.isError ? (
        <TicketErrorState error={query.error} onRetry={query.refetch} />
      ) : query.isPending ? (
        <LoadingSpinner label="Đang tải chi tiết phiếu..." />
      ) : (
        ticket && (
          <>
            <Card className="ticket-summary mb-4">
              <Card.Body>
                <Stack
                  direction="horizontal"
                  gap={2}
                  className="flex-wrap mb-3"
                >
                  <h2 className="h4 mb-0 ticket-number">
                    {ticket.ticketNumber}
                  </h2>
                  <TicketStatusBadge status={ticket.status} />
                  <Badge
                    bg="light"
                    text="dark"
                    className="priority-badge"
                    data-priority={ticket.priority}
                  >
                    {PRIORITY_LABELS[ticket.priority] ?? ticket.priority}
                  </Badge>
                </Stack>
                <h3 className="ticket-title">{ticket.title}</h3>
                <Row as="dl" className="mb-0 ticket-metadata">
                  {[
                    ['Danh mục', ticket.category.name],
                    ['Địa điểm', ticket.location.name],
                    ['Tài sản', ticket.asset?.assetCode ?? '—'],
                    ['Người báo', ticket.reporter.fullName],
                    ...(ticket.currentAssignment !== undefined
                      ? [
                          [
                            'Kỹ thuật viên',
                            ticket.currentAssignment?.technician.fullName ??
                              'Chưa phân công',
                          ],
                        ]
                      : []),
                    ['Hạn SLA', formatTicketTime(ticket.slaDueAt)],
                    ['Ngày tạo', formatTicketTime(ticket.createdAt)],
                    ['Cập nhật', formatTicketTime(ticket.updatedAt)],
                    [
                      'Biểu mẫu đã lưu',
                      `Phiên bản ${ticket.formVersion.versionNumber} · ${FORM_STATUS_LABELS[ticket.formVersion.formStatus] ?? ticket.formVersion.formStatus}`,
                    ],
                  ].map(([label, value]) => (
                    <Col xs={6} lg={3} key={label} className="mt-2">
                      <dt>{label}</dt>
                      <dd>{value}</dd>
                    </Col>
                  ))}
                </Row>
              </Card.Body>
            </Card>
            <TicketActionPanel
              key={`${ticket.id}-${ticketViewerKey(user).join('-')}`}
              ticket={ticket}
            />
            <TechnicianWorkPanel ticket={ticket} />
            <div className="ticket-detail-grid">
              <div>
                <Card className="ticket-section mb-3">
                  <Card.Body>
                    <h2 className="h5">Mô tả</h2>
                    <p
                      className="mb-0 text-break"
                      style={{ whiteSpace: 'pre-wrap' }}
                    >
                      {ticket.description}
                    </p>
                  </Card.Body>
                </Card>
                <TicketDynamicValues
                  formVersion={ticket.formVersion}
                  dynamicValues={ticket.dynamicValues}
                  attachments={ticket.attachments}
                />
              </div>
              <div>
                <TicketStatusHistory history={ticket.statusHistory} />
              </div>
            </div>
            <TicketAttachments
              key={`attachments-${ticket.id}-${ticketViewerKey(user).join('-')}`}
              ticketId={ticket.id}
              attachments={ticket.attachments}
            />
            <TicketComments
              key={`comments-${ticket.id}-${ticketViewerKey(user).join('-')}`}
              ticket={ticket}
            />
            <TicketFeedback
              key={`feedback-${ticket.id}-${ticketViewerKey(user).join('-')}`}
              ticket={ticket}
            />
          </>
        )
      )}
    </Container>
  )
}
