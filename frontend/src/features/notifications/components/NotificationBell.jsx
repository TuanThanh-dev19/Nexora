import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router'
import { FiBell } from 'react-icons/fi'
import { useNotifications } from '../hooks/useNotifications'
import { NotificationDropdown } from './NotificationDropdown'

export function NotificationBell() {
  const navigate = useNavigate()
  const dropdownRef = useRef(null)

  const {
    isOpen,
    toggleDropdown,
    unreadCount,
    notifications,
    pagination,
    isLoadingList,
    isFetchingList,
    listError,
    actionError,
    unreadOnly,
    setUnreadOnly,
    nextPage,
    prevPage,
    markAsRead,
    refresh,
  } = useNotifications()

  // Close dropdown on outside click or Escape key
  useEffect(() => {
    if (!isOpen) return

    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        toggleDropdown(false)
      }
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        toggleDropdown(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen, toggleDropdown])

  const handleSelectItem = async (item) => {
    if (!item) return

    try {
      if (!item.read && !item.readAt) {
        await markAsRead(item.id)
      }
    } catch {
      // actionError is managed by useNotifications hook
    }

    if (item.ticketId) {
      navigate(`/tickets/${item.ticketId}`)
    }
    toggleDropdown(false)
  }

  const bellLabel =
    unreadCount > 0
      ? `Thông báo (${unreadCount} chưa đọc)`
      : 'Thông báo'

  const displayCount = unreadCount > 99 ? '99+' : unreadCount

  return (
    <div className="notification-bell-wrapper" ref={dropdownRef}>
      <button
        type="button"
        className={`notification-bell-btn ${isOpen ? 'active' : ''}`}
        onClick={() => toggleDropdown()}
        aria-label={bellLabel}
        aria-haspopup="dialog"
        aria-expanded={isOpen}
      >
        <FiBell aria-hidden="true" className="notification-bell-icon" />
        {unreadCount > 0 && (
          <span className="notification-bell-badge" aria-hidden="true">
            {displayCount}
          </span>
        )}
      </button>

      {isOpen && (
        <NotificationDropdown
          notifications={notifications}
          unreadCount={unreadCount}
          isLoading={isLoadingList}
          isFetching={isFetchingList}
          error={listError}
          actionError={actionError}
          unreadOnly={unreadOnly}
          onFilterChange={setUnreadOnly}
          onRefresh={refresh}
          pagination={pagination}
          onNextPage={nextPage}
          onPrevPage={prevPage}
          onSelectItem={handleSelectItem}
        />
      )}
    </div>
  )
}

