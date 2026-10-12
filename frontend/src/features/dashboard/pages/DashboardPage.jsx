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

const METRICS = [
  {
    label: 'Open tickets',
    field: 'openTickets',
    icon: FiInbox,
    accent: 'teal',
  },
  {
    label: 'In progress',
    field: 'inProgressTickets',
    icon: FiActivity,
    accent: 'blue',
  },
  {
    label: 'SLA at risk',
    field: 'slaAtRiskTickets',
    icon: FiClock,
    accent: 'amber',
  },
  {
    label: 'Resolved this week',
    field: 'resolvedThisWeek',
    icon: FiCheckCircle,
    accent: 'green',
  },
]

function getScopeDescription(roles = []) {
  if (roles.includes(ROLE_CODES.ADMIN) || roles.includes(ROLE_CODES.MANAGER)) {
    return 'Activity across all tickets.'
  }
  if (roles.includes(ROLE_CODES.TECHNICIAN)) {
    return 'Tickets currently or previously assigned to you.'
  }
  return 'Tickets you submitted.'
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
    summary.data && METRICS.every(({ field }) => summary.data[field] === 0)

  return (
    <Container className="page-container">
      <div className="page-heading">
        <div>
          <p className="eyebrow">KHÔNG GIAN LÀM VIỆC</p>
          <h1 className="h2 mb-1">Dashboard</h1>
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
            <span>Dashboard metrics are unavailable right now.</span>
            <Button
              variant="outline-danger"
              size="sm"
              onClick={summary.refetch}
            >
              Try again
            </Button>
          </div>
        </Alert>
      )}

      <Row className="g-3">
        {METRICS.map(({ label, field, icon, accent }) => (
          <Col xs={6} xl={3} key={field}>
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
          No ticket activity is available for this dashboard yet.
        </Alert>
      )}

      {summary.data?.generatedAt && (
        <p className="small text-secondary mt-3 mb-0">
          Generated {new Date(summary.data.generatedAt).toLocaleString()}
        </p>
      )}
    </Container>
  )
}
