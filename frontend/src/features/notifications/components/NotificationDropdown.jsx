import PropTypes from 'prop-types'
import { Alert, Button, ButtonGroup, Spinner } from 'react-bootstrap'
import { FiAlertCircle, FiBellOff, FiChevronLeft, FiChevronRight, FiRefreshCw } from 'react-icons/fi'
import { NotificationItem } from './NotificationItem'

export function NotificationDropdown({
  notifications,
  unreadCount,
  isLoading,
  isFetching,
  error,
  actionError,
  unreadOnly,
  onFilterChange,
  onRefresh,
  pagination,
  onNextPage,
  onPrevPage,
  onSelectItem,
}) {
  const { page = 0, totalPages = 0 } = pagination || {}

  return (
    <div
      className="notification-dropdown-panel"
      role="region"
      aria-label="Danh sách thông báo"
    >
      <div className="notification-dropdown-header">
        <div className="notification-dropdown-header-top">
          <h2 className="notification-dropdown-title">
            Thông báo
            {unreadCount > 0 && (
              <span className="notification-header-count"> ({unreadCount})</span>
            )}
          </h2>
          <Button
            variant="link"
            size="sm"
            className="notification-refresh-btn"
            onClick={onRefresh}
            title="Làm mới thông báo"
            aria-label="Làm mới thông báo"
            disabled={isFetching}
          >
            <FiRefreshCw className={isFetching ? 'spin' : ''} aria-hidden="true" />
          </Button>
        </div>

        <div className="notification-dropdown-filters">
          <ButtonGroup size="sm" className="w-100">
            <Button
              variant={!unreadOnly ? 'primary' : 'outline-primary'}
              onClick={() => onFilterChange(false)}
              className="notification-filter-btn"
            >
              Tất cả
            </Button>
            <Button
              variant={unreadOnly ? 'primary' : 'outline-primary'}
              onClick={() => onFilterChange(true)}
              className="notification-filter-btn"
            >
              Chưa đọc {unreadCount > 0 && `(${unreadCount})`}
            </Button>
          </ButtonGroup>
        </div>
      </div>

      {actionError && (
        <Alert variant="danger" className="notification-alert-action m-2 p-2">
          <small>{actionError}</small>
        </Alert>
      )}

      <div className="notification-dropdown-body">
        {isLoading ? (
          <div className="notification-loading-container" role="status">
            <Spinner animation="border" size="sm" variant="primary" />
            <span className="ms-2">Đang tải thông báo...</span>
          </div>
        ) : error ? (
          <div className="notification-error-container">
            <FiAlertCircle className="notification-error-icon" aria-hidden="true" />
            <p className="mb-2">Không thể tải thông báo.</p>
            <Button size="sm" variant="outline-secondary" onClick={onRefresh}>
              Thử lại
            </Button>
          </div>
        ) : notifications.length === 0 ? (
          <div className="notification-empty-container">
            <FiBellOff className="notification-empty-icon" aria-hidden="true" />
            <p className="notification-empty-text">
              {unreadOnly
                ? 'Không có thông báo chưa đọc nào.'
                : 'Bạn chưa có thông báo nào.'}
            </p>
          </div>
        ) : (
          <div className="notification-list" role="list">
            {notifications.map((item) => (
              <NotificationItem
                key={item.id}
                item={item}
                onSelect={onSelectItem}
              />
            ))}
          </div>
        )}
      </div>

      {totalPages > 1 && (
        <div className="notification-dropdown-footer">
          <Button
            variant="outline-secondary"
            size="sm"
            disabled={page <= 0 || isFetching}
            onClick={onPrevPage}
            aria-label="Trang trước"
          >
            <FiChevronLeft aria-hidden="true" /> Trước
          </Button>
          <span className="notification-pagination-info">
            Trang {page + 1} / {totalPages}
          </span>
          <Button
            variant="outline-secondary"
            size="sm"
            disabled={page + 1 >= totalPages || isFetching}
            onClick={onNextPage}
            aria-label="Trang sau"
          >
            Sau <FiChevronRight aria-hidden="true" />
          </Button>
        </div>
      )}
    </div>
  )
}

NotificationDropdown.propTypes = {
  notifications: PropTypes.arrayOf(PropTypes.object).isRequired,
  unreadCount: PropTypes.number.isRequired,
  isLoading: PropTypes.bool,
  isFetching: PropTypes.bool,
  error: PropTypes.any,
  actionError: PropTypes.string,
  unreadOnly: PropTypes.bool.isRequired,
  onFilterChange: PropTypes.func.isRequired,
  onRefresh: PropTypes.func.isRequired,
  pagination: PropTypes.shape({
    page: PropTypes.number,
    size: PropTypes.number,
    totalElements: PropTypes.number,
    totalPages: PropTypes.number,
  }),
  onNextPage: PropTypes.func.isRequired,
  onPrevPage: PropTypes.func.isRequired,
  onSelectItem: PropTypes.func.isRequired,
}

