import { Badge } from 'react-bootstrap'

export function TechnicianStatusBadge({ available }) {
  const isAvailable = Boolean(available)

  return (
    <Badge
      bg={isAvailable ? 'success' : 'secondary'}
      className="status-badge"
      data-available={isAvailable ? 'true' : 'false'}
    >
      {isAvailable ? 'Sẵn sàng' : 'Không sẵn sàng'}
    </Badge>
  )
}
