import { Card, Placeholder } from 'react-bootstrap'
import { FiBarChart2 } from 'react-icons/fi'

export function MetricCard({
  label,
  value,
  state = 'success',
  icon: Icon = FiBarChart2,
  accent = 'teal',
}) {
  return (
    <Card
      className={`metric-card metric-${accent} h-100`}
      aria-busy={state === 'loading'}
    >
      <Card.Body>
        <div className="metric-heading">
          <Card.Subtitle className="text-secondary">{label}</Card.Subtitle>
          <span className="metric-icon">
            <Icon aria-hidden="true" />
          </span>
        </div>
        {state === 'loading' ? (
          <Placeholder as="div" animation="glow" className="display-6 mt-2">
            <Placeholder xs={4} aria-label={`Đang tải ${label}`} />
          </Placeholder>
        ) : (
          <div className="metric-value mt-2">
            {state === 'error' ? '—' : value}
          </div>
        )}
        <span className="metric-accent" aria-hidden="true" />
      </Card.Body>
    </Card>
  )
}
