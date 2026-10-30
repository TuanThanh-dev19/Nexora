import { useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Container,
  Form,
  Row,
  Spinner,
  Stack,
  Table,
} from 'react-bootstrap'
import { Link } from 'react-router'
import {
  FiAlertTriangle,
  FiClock,
  FiDownload,
  FiFileText,
  FiFilter,
  FiInbox,
  FiInfo,
} from 'react-icons/fi'
import { useQuery } from '@tanstack/react-query'
import { reportApi } from '../api/reportApi'
import { PRIORITY_LABELS, PRIORITY_OPTIONS } from '../../../shared/constants/priorities'
import {
  TICKET_STATUS_CODES,
} from '../../tickets/constants/ticketStatuses'
import { TicketStatusBadge } from '../../tickets/components/TicketStatusBadge'
import { formatTicketTime } from '../../tickets/utils/ticketFormatting'
import { MetricCard } from '../../dashboard/components/MetricCard'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'

function getDaysAgoIso(days) {
  const d = new Date()
  d.setDate(d.getDate() - days)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getTodayIso() {
  const d = new Date()
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function calculateDayRange(from, to) {
  if (!from || !to) return 0
  const d1 = new Date(`${from}T00:00:00`)
  const d2 = new Date(`${to}T00:00:00`)
  if (Number.isNaN(d1.getTime()) || Number.isNaN(d2.getTime())) return 0
  return Math.round((d2 - d1) / (1000 * 60 * 60 * 24)) + 1
}

export function TicketReportPage() {
  const defaultFrom = getDaysAgoIso(6)
  const defaultTo = getTodayIso()

  // Form input state (local changes before clicking "Áp dụng")
  const [formValues, setFormValues] = useState({
    fromDate: defaultFrom,
    toDate: defaultTo,
    locationId: '',
    priority: '',
  })

  // Applied filter state (used for queries and CSV exports)
  const [appliedFilters, setAppliedFilters] = useState({
    fromDate: defaultFrom,
    toDate: defaultTo,
    locationId: '',
    priority: '',
  })

  const [page, setPage] = useState(0)
  const size = 20

  const [validationError, setValidationError] = useState('')
  const [isExporting, setIsExporting] = useState(false)
  const [exportError, setExportError] = useState('')

  // Build query params from appliedFilters
  const queryParams = {
    fromDate: appliedFilters.fromDate,
    toDate: appliedFilters.toDate,
    page,
    size,
  }
  if (appliedFilters.locationId) {
    queryParams.locationId = appliedFilters.locationId
  }
  if (appliedFilters.priority) {
    queryParams.priority = appliedFilters.priority
  }

  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: ['ticket-report', queryParams],
    queryFn: ({ signal }) => reportApi.getTicketReport(queryParams, { signal }),
  })

  const handleApplyFilters = (e) => {
    e.preventDefault()
    setValidationError('')
    setExportError('')

    if (!formValues.fromDate || !formValues.toDate) {
      setValidationError('Vui lòng chọn đầy đủ ngày bắt đầu và ngày kết thúc.')
      return
    }

    if (formValues.fromDate > formValues.toDate) {
      setValidationError('Ngày bắt đầu phải nhỏ hơn hoặc bằng ngày kết thúc.')
      return
    }

    const range = calculateDayRange(formValues.fromDate, formValues.toDate)
    if (range > 92) {
      setValidationError('Khoảng thời gian báo cáo tối đa là 92 ngày.')
      return
    }

    setPage(0)
    setAppliedFilters({ ...formValues })
  }

  const handleExportCsv = async () => {
    setIsExporting(true)
    setExportError('')

    const exportParams = {
      fromDate: appliedFilters.fromDate,
      toDate: appliedFilters.toDate,
    }
    if (appliedFilters.locationId) {
      exportParams.locationId = appliedFilters.locationId
    }
    if (appliedFilters.priority) {
      exportParams.priority = appliedFilters.priority
    }

    try {
      const response = await reportApi.exportTicketReportCsv(exportParams)
      const disposition = response.headers?.['content-disposition']
      let filename = `ticket-report_${appliedFilters.fromDate}_to_${appliedFilters.toDate}.csv`
      if (disposition) {
        const match = disposition.match(/filename="?([^";]+)"?/)
        if (match && match[1]) {
          filename = match[1]
        }
      }

      const blob = new Blob([response.data], { type: 'text/csv; charset=UTF-8' })
      const downloadUrl = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = downloadUrl
      link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(downloadUrl)
    } catch (err) {
      let message = 'Xuất file thất bại. Vui lòng thử lại.'
      if (err.response?.data instanceof Blob) {
        try {
          const text = await err.response.data.text()
          const json = JSON.parse(text)
          if (json.detail) message = json.detail
        } catch {
          // ignore parsing error
        }
      } else if (err.response?.data?.detail) {
        message = err.response.data.detail
      }
      setExportError(message)
    } finally {
      setIsExporting(false)
    }
  }

  const summary = data?.summary
  const ticketPage = data?.tickets
  const totalTickets = summary?.totalTickets ?? 0
  const overdueTickets = summary?.overdueTickets ?? 0
  const statusCounts = summary?.statusCounts ?? {}

  return (
    <Container className="page-container">
      <div className="page-heading">
        <div>
          <p className="eyebrow">BÁO CÁO & THỐNG KÊ</p>
          <h1 className="h2 mb-1">Báo cáo Ticket</h1>
          <p className="text-secondary mb-0">
            Báo cáo tổng hợp và chi tiết Ticket theo khoảng thời gian dành cho Quản lý / Quản trị viên.
          </p>
        </div>
        <span className="page-heading-icon">
          <FiFileText aria-hidden="true" />
        </span>
      </div>

      <Alert variant="info" className="d-flex align-items-center gap-2 mb-4">
        <FiInfo className="flex-shrink-0" />
        <span>Ticket được tạo trong khoảng ngày đã chọn; trạng thái cập nhật tại thời điểm lập báo cáo.</span>
      </Alert>

      {/* Filter Form Card */}
      <Card className="mb-4">
        <Card.Body>
          <Form onSubmit={handleApplyFilters}>
            <Row className="g-3 align-items-end">
              <Col xs={12} sm={6} lg={3}>
                <Form.Group controlId="report-fromDate">
                  <Form.Label>Từ ngày (Asia/Bangkok)</Form.Label>
                  <Form.Control
                    type="date"
                    value={formValues.fromDate}
                    onChange={(e) => setFormValues((prev) => ({ ...prev, fromDate: e.target.value }))}
                    required
                  />
                </Form.Group>
              </Col>
              <Col xs={12} sm={6} lg={3}>
                <Form.Group controlId="report-toDate">
                  <Form.Label>Đến ngày (Asia/Bangkok)</Form.Label>
                  <Form.Control
                    type="date"
                    value={formValues.toDate}
                    onChange={(e) => setFormValues((prev) => ({ ...prev, toDate: e.target.value }))}
                    required
                  />
                </Form.Group>
              </Col>
              <Col xs={12} sm={6} lg={2}>
                <Form.Group controlId="report-priority">
                  <Form.Label>Mức ưu tiên</Form.Label>
                  <Form.Select
                    value={formValues.priority}
                    onChange={(e) => setFormValues((prev) => ({ ...prev, priority: e.target.value }))}
                  >
                    <option value="">Tất cả</option>
                    {PRIORITY_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </Form.Select>
                </Form.Group>
              </Col>
              <Col xs={12} sm={6} lg={2}>
                <Form.Group controlId="report-locationId">
                  <Form.Label>Mã vị trí (ID)</Form.Label>
                  <Form.Control
                    type="number"
                    min="1"
                    placeholder="VD: 1"
                    value={formValues.locationId}
                    onChange={(e) => setFormValues((prev) => ({ ...prev, locationId: e.target.value }))}
                  />
                </Form.Group>
              </Col>
              <Col xs={12} lg={2}>
                <Stack direction="horizontal" gap={2} className="w-100">
                  <Button type="submit" variant="primary" className="flex-grow-1" disabled={isLoading || isFetching}>
                    <FiFilter aria-hidden="true" /> Áp dụng
                  </Button>
                  <Button
                    type="button"
                    variant="outline-success"
                    onClick={handleExportCsv}
                    disabled={isExporting || isLoading}
                    title="Xuất dữ liệu theo bộ lọc đã áp dụng"
                  >
                    {isExporting ? (
                      <Spinner size="sm" animation="border" />
                    ) : (
                      <>
                        <FiDownload aria-hidden="true" /> CSV
                      </>
                    )}
                  </Button>
                </Stack>
              </Col>
            </Row>
          </Form>

          {validationError && (
            <Alert variant="danger" className="mt-3 mb-0" role="alert">
              {validationError}
            </Alert>
          )}

          {exportError && (
            <Alert variant="danger" className="mt-3 mb-0" role="alert">
              <FiAlertTriangle className="me-2" />
              {exportError}
            </Alert>
          )}
        </Card.Body>
      </Card>

      {/* Query Error State */}
      {isError && (
        <Alert variant="danger" role="alert" className="mb-4">
          <div className="d-flex flex-wrap align-items-center justify-content-between gap-2">
            <span>
              {error?.response?.data?.detail || 'Không thể tải dữ liệu báo cáo. Vui lòng kiểm tra lại bộ lọc.'}
            </span>
            <Button variant="outline-danger" size="sm" onClick={() => refetch()} disabled={isFetching}>
              {isFetching ? 'Đang tải lại…' : 'Thử lại'}
            </Button>
          </div>
        </Alert>
      )}

      {/* Metrics Summary Cards */}
      <Row className="g-3 mb-4">
        <Col xs={12} sm={6} md={3}>
          <MetricCard
            label="Tổng số Ticket"
            value={totalTickets}
            state={isLoading ? 'loading' : isError ? 'error' : 'success'}
            icon={FiInbox}
            accent="teal"
          />
        </Col>
        <Col xs={12} sm={6} md={3}>
          <MetricCard
            label="Đang quá hạn SLA"
            value={overdueTickets}
            state={isLoading ? 'loading' : isError ? 'error' : 'success'}
            icon={FiClock}
            accent="amber"
          />
        </Col>
        <Col xs={12} md={6}>
          <Card className="h-100">
            <Card.Body>
              <Card.Subtitle className="text-secondary mb-3">Phân bố theo trạng thái</Card.Subtitle>
              <div className="d-flex flex-wrap gap-2">
                {Object.values(TICKET_STATUS_CODES).map((statusCode) => {
                  const count = statusCounts[statusCode] ?? 0
                  return (
                    <div
                      key={statusCode}
                      className="d-flex align-items-center border rounded px-2 py-1 bg-light text-nowrap"
                    >
                      <TicketStatusBadge status={statusCode} />
                      <span className="ms-2 fw-semibold">{count}</span>
                    </div>
                  )
                })}
              </div>
            </Card.Body>
          </Card>
        </Col>
      </Row>

      {/* Detail Table */}
      <Card>
        <Card.Header className="d-flex justify-content-between align-items-center bg-white py-3">
          <h2 className="h5 mb-0">Danh sách Ticket ({ticketPage?.totalElements ?? 0})</h2>
          {summary?.generatedAt && (
            <small className="text-muted">
              Thời điểm lập báo cáo: {formatTicketTime(summary.generatedAt)} ({summary.timeZone})
            </small>
          )}
        </Card.Header>
        <Card.Body className="p-0">
          {isLoading ? (
            <div className="py-5 text-center">
              <LoadingSpinner message="Đang tải dữ liệu báo cáo..." />
            </div>
          ) : totalTickets === 0 ? (
            <div className="py-5">
              <EmptyState message="Không tìm thấy Ticket nào trong khoảng thời gian đã chọn." />
            </div>
          ) : (
            <Table responsive hover className="align-middle mb-0">
              <thead className="table-light">
                <tr>
                  <th>Mã phiếu</th>
                  <th>Tiêu đề</th>
                  <th>Trạng thái</th>
                  <th>Ưu tiên</th>
                  <th>Vị trí</th>
                  <th>Hạn SLA</th>
                  <th>Ngày tạo</th>
                </tr>
              </thead>
              <tbody>
                {ticketPage?.content?.map((ticket) => (
                  <tr key={ticket.id}>
                    <td>
                      <Link to={`/tickets/${ticket.id}`} className="fw-semibold text-decoration-none">
                        {ticket.ticketNumber}
                      </Link>
                    </td>
                    <td>{ticket.title}</td>
                    <td>
                      <TicketStatusBadge status={ticket.status} />
                    </td>
                    <td>
                      <Badge bg="light" text="dark" className="border">
                        {PRIORITY_LABELS[ticket.priority] ?? ticket.priority}
                      </Badge>
                    </td>
                    <td>{ticket.location?.name ?? '—'}</td>
                    <td>{formatTicketTime(ticket.slaDueAt)}</td>
                    <td>{formatTicketTime(ticket.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card.Body>

        {ticketPage?.totalPages > 1 && (
          <Card.Footer className="bg-white d-flex justify-content-between align-items-center py-2">
            <span className="text-muted small">
              Trang {ticketPage.page + 1} / {ticketPage.totalPages}
            </span>
            <Stack direction="horizontal" gap={2}>
              <Button
                variant="outline-secondary"
                size="sm"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={ticketPage.page === 0}
              >
                Trước
              </Button>
              <Button
                variant="outline-secondary"
                size="sm"
                onClick={() => setPage((p) => p + 1)}
                disabled={ticketPage.page >= ticketPage.totalPages - 1}
              >
                Sau
              </Button>
            </Stack>
          </Card.Footer>
        )}
      </Card>
    </Container>
  )
}
