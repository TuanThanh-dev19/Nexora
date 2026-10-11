import { Alert, Button, Col, Container, Row } from 'react-bootstrap'
import {
  FiActivity,
  FiCheckCircle,
  FiClock,
  FiInbox,
  FiPieChart,
} from 'react-icons/fi'
import { ROLE_CODES } from '../../../shared/constants/roles'
import { useAuth } from '../../auth/context/useAuth'
import { MetricCard } from '../components/MetricCard'
import { useDashboardSummary } from '../hooks/useDashboardSummary'
import { formatDashboardTime } from '../utils/formatDashboardTime'

const METRICS = [
  {
    label: 'Phiếu đang mở',
    field: 'openTickets',
    icon: FiInbox,
    accent: 'teal',
  },
  {
    label: 'Đang xử lý',
    field: 'inProgressTickets',
    icon: FiActivity,
    accent: 'blue',
  },
  {
    label: 'Có nguy cơ trễ SLA',
    field: 'slaAtRiskTickets',
    icon: FiClock,
    accent: 'amber',
  },
  {
    label: 'Đã giải quyết tuần này',
    field: 'resolvedThisWeek',
    icon: FiCheckCircle,
    accent: 'green',
  },
]

function getScopeDescription(roles = []) {
  if (roles.includes(ROLE_CODES.ADMIN) || roles.includes(ROLE_CODES.MANAGER)) {
    return 'Tổng quan toàn bộ phiếu yêu cầu.'
  }
  if (roles.includes(ROLE_CODES.TECHNICIAN)) {
    return 'Phiếu được phân công cho bạn hiện tại hoặc trong lịch sử.'
  }
  return 'Phiếu yêu cầu do chính bạn gửi.'
}

export function DashboardPage() {
  const { user } = useAuth()
  const summary = useDashboardSummary()
  const state = summary.isPending
    ? 'loading'
    : summary.isError
      ? 'error'
      : 'success'
  const isEmpty =
    state === 'success' &&
    summary.data &&
    METRICS.every(({ field }) => summary.data[field] === 0)

  return (
    <Container className="page-container">
      <div className="page-heading">
        <div>
          <p className="eyebrow">KHÔNG GIAN LÀM VIỆC</p>
          <h1 className="h2 mb-1">Tổng quan</h1>
          <p className="text-secondary mb-0">
            {getScopeDescription(user?.roles)}
          </p>
        </div>
        <span className="page-heading-icon">
          <FiPieChart aria-hidden="true" />
        </span>
      </div>

      {summary.isError && (
        <Alert variant="danger" role="alert">
          <div className="d-flex flex-wrap align-items-center justify-content-between gap-2">
            <span>
              Chưa tải được số liệu tổng quan. API dữ liệu thật đang chờ triển
              khai; không hiển thị số liệu giả.
            </span>
            <Button
              variant="outline-danger"
              size="sm"
              onClick={summary.refetch}
              disabled={summary.isFetching}
            >
              {summary.isFetching ? 'Đang tải lại…' : 'Thử lại'}
            </Button>
          </div>
        </Alert>
      )}

      <Row className="g-3">
        {METRICS.map(({ label, field, icon, accent }) => (
          <Col xs={12} sm={6} lg={3} key={field}>
            <MetricCard
              label={label}
              value={summary.data?.[field] ?? 0}
              state={state}
              icon={icon}
              accent={accent}
            />
          </Col>
        ))}
      </Row>

      {isEmpty && (
        <Alert
          variant="light"
          className="dashboard-empty-state mt-4"
          role="status"
        >
          Chưa có hoạt động phiếu yêu cầu trong phạm vi của bạn.
        </Alert>
      )}

      {state === 'success' && summary.data?.generatedAt && (
        <p className="small text-secondary mt-3 mb-0">
          Cập nhật: {formatDashboardTime(summary.data.generatedAt)}{' '}
          (Asia/Bangkok)
        </p>
      )}
    </Container>
  )
}
