import PropTypes from 'prop-types'
import { Badge } from 'react-bootstrap'
import { FiCheckCircle, FiClock, FiExternalLink } from 'react-icons/fi'
import { formatDate } from '../../../shared/utils/formatDate'

export function NotificationItem({ item, onSelect }) {
  if (!item) return null

  const isUnread = !item.read && !item.readAt

  const handleClick = (e) => {
    e.preventDefault()
    if (onSelect) {
      onSelect(item)
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      if (onSelect) {
        onSelect(item)
      }
    }
  }

  return (
    <div
      role="button"
      tabIndex={0}
      className={`notification-item ${isUnread ? 'notification-item-unread' : 'notification-item-read'}`}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      aria-label={`${item.title} - ${isUnread ? 'Chưa đọc' : 'Đã đọc'}`}
    >
      <div className="notification-item-header">
        <div className="notification-item-title-wrapper">
          <span className="notification-item-title">{item.title}</span>
          {isUnread && (
            <Badge bg="primary" pill className="notification-badge-unread">
              Mới
            </Badge>
          )}
        </div>
        <span className="notification-item-time" title={item.createdAt}>
          <FiClock aria-hidden="true" /> {formatDate(item.createdAt)}
        </span>
      </div>
      <p className="notification-item-message">{item.message}</p>
      <div className="notification-item-footer">
        {item.ticketId && (
          <span className="notification-item-ticket-link">
            Xem phiếu #{item.ticketId} <FiExternalLink aria-hidden="true" />
          </span>
        )}
        {!isUnread && (
          <span className="notification-item-status-read">
            <FiCheckCircle aria-hidden="true" /> Đã đọc
          </span>
        )}
      </div>
    </div>
  )
}

NotificationItem.propTypes = {
  item: PropTypes.shape({
    id: PropTypes.string.isRequired,
    ticketId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    title: PropTypes.string.isRequired,
    message: PropTypes.string.isRequired,
    createdAt: PropTypes.string,
    read: PropTypes.bool,
    readAt: PropTypes.string,
  }).isRequired,
  onSelect: PropTypes.func.isRequired,
}

