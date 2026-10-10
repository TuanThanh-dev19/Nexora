import {
  Badge,
  Button,
  Card,
  Col,
  Container,
  Form,
  Stack,
  Table,
} from 'react-bootstrap'
import { Link, useLocation } from 'react-router'
import { FiClipboard } from 'react-icons/fi'
import { PRIORITY_LABELS } from '../../../shared/constants/priorities'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { TicketErrorState } from '../components/TicketErrorState'
import { TicketFilters } from '../components/TicketFilters'
import { TicketStatusBadge } from '../components/TicketStatusBadge'
import { useAssignedTickets } from '../hooks/useTechnicianWork'
import {
  TICKET_SORT_OPTIONS,
  useTicketSearchParams,
} from '../hooks/useTicketSearchParams'
import { slaState } from '../constants/technicianWork'
import { formatTicketTime } from '../utils/ticketFormatting'

const WORK_STATUSES = [
  { value: 'ASSIGNED', label: 'Đã phân công' },
  { value: 'IN_PROGRESS', label: 'Đang xử lý' },
]

export function MyWorkPage() {
  const { params, requestSearch, update, reset } = useTicketSearchParams()
  const query = useAssignedTickets(requestSearch)
  const location = useLocation()
  const data = query.data
  return (
    <Container className="page-container">
      <div className="page-heading">
        <div>
          <p className="eyebrow">KỸ THUẬT VIÊN</p>
          <h1 className="h2">Công việc của tôi</h1>
          <p className="text-secondary mb-0">
            Các phiếu đang được giao cho bạn.
          </p>
        </div>
        <span className="page-heading-icon">
          <FiClipboard aria-hidden="true" />
        </span>
      </div>
      {!query.available ? (
        <Card>
          <Card.Body>
            <p className="mb-0">
              Danh sách công việc chưa khả dụng. API phân công hiện tại sẽ được
              bổ sung sau.
            </p>
          </Card.Body>
        </Card>
      ) : (
        <Card className="ticket-list-card">
          <Card.Body className="ticket-list-body">
            <TicketFilters
              {...params}
              statusOptions={WORK_STATUSES}
              onChange={update}
              onReset={reset}
            >
              <Col xs={6} xl={3}>
                <Form.Label htmlFor="work-sort">Sắp xếp</Form.Label>
                <Form.Select
                  id="work-sort"
                  value={params.sort}
                  onChange={(event) => update({ sort: event.target.value })}
                >
                  {TICKET_SORT_OPTIONS.map(({ value, label }) => (
                    <option value={value} key={value}>
                      {label}
                    </option>
                  ))}
                </Form.Select>
              </Col>
              <Col xs={6} xl={3}>
                <Form.Label htmlFor="work-size">Số phiếu mỗi trang</Form.Label>
                <Form.Select
                  id="work-size"
                  value={params.size}
                  onChange={(event) => update({ size: event.target.value })}
                >
                  {[...new Set(['10', '20', '50', '100', params.size])]
                    .sort((a, b) => Number(a) - Number(b))
                    .map((size) => (
                      <option value={size} key={size}>
                        {size}
                      </option>
                    ))}
                </Form.Select>
              </Col>
            </TicketFilters>
            <div className="ticket-results-heading">
              <h2>Công việc hiện tại</h2>
              {data && !query.isError && (
                <span className="result-count">{data.totalElements} phiếu</span>
              )}
            </div>
            {query.isError ? (
              <TicketErrorState
                error={query.error}
                onRetry={query.refetch}
                onReset={reset}
              />
            ) : query.isPending ? (
              <LoadingSpinner label="Đang tải công việc..." />
            ) : (
              data && (
                <>
                  {data.content.length === 0 ? (
                    <EmptyState message="Không có công việc phù hợp." />
                  ) : (
                    <Table
                      responsive
                      hover
                      className="align-middle ticket-table"
                    >
                      <thead>
                        <tr>
                          {[
                            'Số phiếu',
                            'Trạng thái',
                            'Ưu tiên',
                            'Địa điểm',
                            'Phân công',
                            'Hạn SLA',
                            'SLA',
                          ].map((label) => (
                            <th key={label}>{label}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {data.content.map((ticket) => (
                          <tr key={ticket.id}>
                            <td>
                              <Link
                                to={`/tickets/${ticket.id}`}
                                state={{
                                  listFrom: 'my-work',
                                  listSearch: location.search,
                                }}
                                className="ticket-number-link"
                              >
                                {ticket.ticketNumber}
                              </Link>
                            </td>
                            <td>
                              <TicketStatusBadge status={ticket.status} />
                            </td>
                            <td>
                              <Badge
                                bg="light"
                                text="dark"
                                className="priority-badge"
                                data-priority={ticket.priority}
                              >
                                {PRIORITY_LABELS[ticket.priority] ??
                                  ticket.priority}
                              </Badge>
                            </td>
                            <td>{ticket.location.name}</td>
                            <td>
                              {formatTicketTime(
                                ticket.currentAssignment.assignedAt,
                              )}
                            </td>
                            <td>{formatTicketTime(ticket.slaDueAt)}</td>
                            <td>
                              {slaState(ticket) === 'BREACHED'
                                ? 'Quá hạn'
                                : slaState(ticket) === 'AT_RISK'
                                  ? 'Sắp đến hạn'
                                  : slaState(ticket) === 'ON_TIME'
                                    ? 'Trong hạn'
                                    : '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </Table>
                  )}
                  <Stack
                    direction="horizontal"
                    gap={2}
                    className="ticket-pagination justify-content-between flex-wrap"
                    aria-label="Phân trang"
                  >
                    <span>
                      {data.totalElements} phiếu · Trang {data.page + 1}/
                      {Math.max(1, data.totalPages)}
                    </span>
                    <Stack direction="horizontal" gap={2}>
                      <Button
                        variant="outline-secondary"
                        disabled={data.page === 0}
                        onClick={() => update({ page: data.page - 1 })}
                      >
                        Trang trước
                      </Button>
                      <Button
                        variant="outline-secondary"
                        disabled={data.page + 1 >= data.totalPages}
                        onClick={() => update({ page: data.page + 1 })}
                      >
                        Trang sau
                      </Button>
                    </Stack>
                  </Stack>
                </>
              )
            )}
          </Card.Body>
        </Card>
      )}
    </Container>
  )
}
