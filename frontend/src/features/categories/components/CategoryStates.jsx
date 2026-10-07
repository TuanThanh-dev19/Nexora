import { Alert, Button, Spinner } from 'react-bootstrap'

import { errorMessage } from '../api/categoryError'
export function QueryState({ query, children }) {
  if (query.isPending)
    return (
      <p role="status">
        <Spinner as="span" size="sm" className="me-2" />
        Loading…
      </p>
    )
  if (query.isError)
    return (
      <Alert variant="danger" role="alert">
        {errorMessage(query.error)}{' '}
        <Button
          variant="outline-danger"
          size="sm"
          onClick={() => query.refetch()}
        >
          Retry
        </Button>
      </Alert>
    )
  return children
}

export function FieldErrors({ errors }) {
  const entries = Object.entries(errors)
  if (!entries.length) return null
  return (
    <Alert variant="danger" role="alert">
      <strong>Please correct the following:</strong>
      <ul className="mb-0">
        {entries.map(([field, message]) => (
          <li key={field}>
            <strong>{field}</strong>: {message}
          </li>
        ))}
      </ul>
    </Alert>
  )
}
