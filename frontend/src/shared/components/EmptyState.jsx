import { Alert } from 'react-bootstrap'
import { FiInbox } from 'react-icons/fi'

export function EmptyState({ message }) {
  return (
    <Alert variant="light" className="empty-state" role="status">
      <span className="state-icon">
        <FiInbox aria-hidden="true" />
      </span>
      <p className="mb-0">{message}</p>
    </Alert>
  )
}
