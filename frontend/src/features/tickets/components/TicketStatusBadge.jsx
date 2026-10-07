import { Badge } from 'react-bootstrap'
import {
  TICKET_STATUS_CODES,
  TICKET_STATUS_LABELS,
} from '../constants/ticketStatuses'

const variants = {
  [TICKET_STATUS_CODES.SUBMITTED]: 'secondary',
  [TICKET_STATUS_CODES.UNDER_REVIEW]: 'info',
  [TICKET_STATUS_CODES.ASSIGNED]: 'primary',
  [TICKET_STATUS_CODES.IN_PROGRESS]: 'warning',
  [TICKET_STATUS_CODES.RESOLVED]: 'success',
  [TICKET_STATUS_CODES.REOPENED]: 'warning',
  [TICKET_STATUS_CODES.CLOSED]: 'dark',
  [TICKET_STATUS_CODES.REJECTED]: 'danger',
  [TICKET_STATUS_CODES.CANCELLED]: 'secondary',
}

export function TicketStatusBadge({ status }) {
  const label = TICKET_STATUS_LABELS[status] ?? status ?? 'Không xác định'

  return (
    <Badge
      bg={variants[status] ?? 'secondary'}
      className="status-badge"
      data-status={status}
    >
      {label}
    </Badge>
  )
}
