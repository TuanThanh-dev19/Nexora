import { Badge } from 'react-bootstrap'
import {
  USER_STATUS_CODES,
  USER_STATUS_LABELS,
} from '../../../shared/constants/userStatuses'

const statusVariants = {
  [USER_STATUS_CODES.ACTIVE]: 'success',
  [USER_STATUS_CODES.LOCKED]: 'warning',
  [USER_STATUS_CODES.DISABLED]: 'secondary',
}

export function UserStatusBadge({ status }) {
  const label = USER_STATUS_LABELS[status] ?? status ?? 'Không xác định'
  const variant = statusVariants[status] ?? 'secondary'

  return (
    <Badge
      bg={variant}
      className="status-badge"
      data-status={status}
    >
      {label}
    </Badge>
  )
}
