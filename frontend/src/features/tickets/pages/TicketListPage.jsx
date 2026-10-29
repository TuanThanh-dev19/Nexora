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
import { FiArrowUpRight, FiClipboard } from 'react-icons/fi'
import { PRIORITY_LABELS } from '../../../shared/constants/priorities'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { TicketFilters } from '../components/TicketFilters'
import { TicketKeywordSearch } from '../components/TicketKeywordSearch'
import { TicketStatusBadge } from '../components/TicketStatusBadge'
import { TicketErrorState } from '../components/TicketErrorState'
import { useTickets } from '../hooks/useTickets'
import {
  TICKET_SORT_OPTIONS,
  useTicketSearchParams,
} from '../hooks/useTicketSearchParams'
import { formatTicketTime } from '../utils/ticketFormatting'
import { useAuth } from '../../auth/context/useAuth'

export function TicketListPage() {
  const { user } = useAuth()
  const { params, requestSearch, update, reset } = useTicketSearchParams()
  const query = useTickets(requestSearch)
  const location = useLocation()

  return (
    <Container className="page-container">
      <div className="page-heading">
        <div>
          <p className="eyebrow">QUẢN LÝ SỰ CỐ</p>
          <h1 className="h2">Phiếu yêu cầu</h1>
          <p className="text-secondary mb-0">
            Danh sách phiếu trong phạm vi bạn được phép xem.
          </p>
        </div>
        {user?.roles?.includes('REQUESTER') && (
          <Button as={Link} to="/tickets/new">
            Tạo phiếu
          </Button>
        )}
        <span className="page-heading-icon">
          <FiClipboard aria-hidden="true" />
        </span>
      </div>
      <Card className="ticket-list-card">
        <Card.Body className="ticket-list-body">
          <TicketKeywordSearch
            key={requestSearch}
            keyword={params.keyword}
            onSearch={(keyword) => update({ keyword })}
          />
          <TicketFilters {...params} onChange={update} onReset={reset}>
            <Col xs={6} xl={3}>
              <Form.Label htmlFor="ticket-sort">Sắp xếp</Form.Label>
              <Form.Select
                id="ticket-sort"
                value={params.sort}
                onChange={(event) => update({ sort: event.target.value })}
              >
                {TICKET_SORT_OPTIONS.map(({ value, label }) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Form.Select>
            </Col>
            <Col xs={6} xl={3}>
              <Form.Label htmlFor="ticket-size">Số phiếu mỗi trang</Form.Label>
              <Form.Select
                id="ticket-size"
                value={params.size}
                onChange={(event) => update({ size: event.target.value })}
              >
                {[...new Set(['10', '20', '50', '100', params.size])]
                  .sort((a, b) => Number(a) - Number(b))
                  .map((size) => (
                    <option key={size} value={size}>
                      {size}
                    </option>
                  ))}
              </Form.Select>
            </Col>
          </TicketFilters>
          <div className="ticket-results-heading">
            <h2>Danh sách phiếu</h2>
            {!query.isError && query.data && (
              <span className="result-count">
                {query.data.totalElements} phiếu
              </span>
            )}
          </div>
          <TicketResults
            query={query}
            update={update}
            reset={reset}
            listSearch={location.search}
          />
        </Card.Body>
      </Card>
    </Container>
  )
}

function TicketResults({ query, update, reset, listSearch }) {
  // An error takes precedence over cached rows after access is revoked.
  if (query.isError) {
    return (
      <TicketErrorState
        error={query.error}
        onRetry={query.refetch}
        onReset={reset}
      />
    )
  }
  if (query.isPending)
    return <LoadingSpinner label="Đang tải phiếu yêu cầu..." />
  const data = query.data
  if (!data) return null

  return (
    <>
      {data.content.length === 0 ? (
        <EmptyState message="Không có phiếu yêu cầu phù hợp." />
      ) : (
        <Table responsive hover className="align-middle ticket-table">
          <thead>
            <tr>
              {[
                'Số phiếu',
                'Trạng thái',
                'Ưu tiên',
                'Danh mục',
                'Địa điểm',
                'Tài sản',
                'Hạn SLA',
                'Ngày tạo',
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
                    state={{ listSearch }}
                    className="ticket-number-link"
                  >
                    {ticket.ticketNumber}
                    <FiArrowUpRight aria-hidden="true" />
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
                    {PRIORITY_LABELS[ticket.priority] ?? ticket.priority}
                  </Badge>
                </td>
                <td>{ticket.category.name}</td>
                <td>{ticket.location.name}</td>
                <td>{ticket.asset?.assetCode ?? '—'}</td>
                <td>{formatTicketTime(ticket.slaDueAt)}</td>
                <td>{formatTicketTime(ticket.createdAt)}</td>
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
          {data.page >= data.totalPages && data.page > 0 && (
            <Button
              variant="outline-secondary"
              onClick={() => update({ page: 0 })}
            >
              Trang đầu
            </Button>
          )}
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
}
