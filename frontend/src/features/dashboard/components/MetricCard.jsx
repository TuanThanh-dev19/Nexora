import { Card, Placeholder } from 'react-bootstrap'

export function MetricCard({ label, value, state = 'success' }) {
  return (
    <Card
      className="metric-card h-100 shadow-sm"
      aria-busy={state === 'loading'}
    >
      <Card.Body>
        <Card.Subtitle className="text-secondary">{label}</Card.Subtitle>
        {state === 'loading' ? (
          <Placeholder as="div" animation="glow" className="display-6 mt-2">
            <Placeholder xs={4} aria-label={`Loading ${label}`} />
          </Placeholder>
        ) : (
          <div className="display-6 mt-2">
            {state === 'error' ? '—' : value}
          </div>
        )}
      </Card.Body>
    </Card>
  )
}
