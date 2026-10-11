import { Badge } from 'react-bootstrap'
import { ROLE_CODES, ROLE_LABELS } from '../../../shared/constants/roles'

const roleBadgeVariants = {
  [ROLE_CODES.ADMIN]: { bg: 'primary', className: 'text-white' },
  [ROLE_CODES.MANAGER]: { bg: 'info', className: 'text-dark' },
  [ROLE_CODES.TECHNICIAN]: { bg: 'secondary', className: 'text-white' },
  [ROLE_CODES.REQUESTER]: { bg: 'light', className: 'text-dark border' },
}

export function UserRoleBadges({ roles = [] }) {
  if (!roles || roles.length === 0) {
    return <span className="text-muted small">—</span>
  }

  return (
    <div className="d-flex flex-wrap gap-1 align-items-center">
      {roles.map((role) => {
        const config = roleBadgeVariants[role] ?? { bg: 'secondary', className: 'text-white' }
        const label = ROLE_LABELS[role] ?? role
        return (
          <Badge
            key={role}
            bg={config.bg}
            className={`role-badge ${config.className}`}
            data-role={role}
          >
            {label}
          </Badge>
        )
      })}
    </div>
  )
}
