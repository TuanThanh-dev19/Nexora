import { Spinner } from 'react-bootstrap'

export function LoadingSpinner({ label = 'Loading' }) {
  return (
    <div className="loading-state" role="status">
      <Spinner animation="border" size="sm" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}
