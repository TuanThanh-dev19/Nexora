import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useNotifications } from '../hooks/useNotifications'
import { NotificationBell } from './NotificationBell'

const mockNavigate = vi.fn()
vi.mock('react-router', async () => {
  const actual = await vi.importActual('react-router')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

vi.mock('../hooks/useNotifications', () => ({
  useNotifications: vi.fn(),
}))

describe('NotificationBell component', () => {
  const defaultHookReturn = {
    isOpen: false,
    toggleDropdown: vi.fn(),
    unreadCount: 0,
    notifications: [],
    pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
    isLoadingList: false,
    isFetchingList: false,
    listError: null,
    actionError: null,
    unreadOnly: false,
    setUnreadOnly: vi.fn(),
    nextPage: vi.fn(),
    prevPage: vi.fn(),
    markAsRead: vi.fn(),
    refresh: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
    useNotifications.mockReturnValue(defaultHookReturn)
  })

  it('renders bell button without badge when unreadCount is 0', () => {
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    const bellBtn = screen.getByRole('button', { name: 'Thông báo' })
    expect(bellBtn).toBeInTheDocument()
    expect(document.querySelector('.notification-bell-badge')).toBeNull()
  })

  it('renders badge with count when unreadCount > 0', () => {
    useNotifications.mockReturnValue({
      ...defaultHookReturn,
      unreadCount: 5,
    })

    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    const bellBtn = screen.getByRole('button', {
      name: 'Thông báo (5 chưa đọc)',
    })
    expect(bellBtn).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
  })

  it('renders "99+" badge when unreadCount exceeds 99', () => {
    useNotifications.mockReturnValue({
      ...defaultHookReturn,
      unreadCount: 150,
    })

    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    expect(screen.getByText('99+')).toBeInTheDocument()
  })

  it('toggles dropdown on click', async () => {
    const toggleDropdown = vi.fn()
    useNotifications.mockReturnValue({
      ...defaultHookReturn,
      toggleDropdown,
    })

    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    const bellBtn = screen.getByRole('button', { name: 'Thông báo' })
    await user.click(bellBtn)

    expect(toggleDropdown).toHaveBeenCalledTimes(1)
  })

  it('renders dropdown when isOpen is true', () => {
    useNotifications.mockReturnValue({
      ...defaultHookReturn,
      isOpen: true,
      unreadCount: 2,
    })

    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('region', { name: 'Danh sách thông báo' }),
    ).toBeInTheDocument()
  })

  it('closes dropdown when Escape key is pressed', async () => {
    const toggleDropdown = vi.fn()
    useNotifications.mockReturnValue({
      ...defaultHookReturn,
      isOpen: true,
      toggleDropdown,
    })

    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    await user.keyboard('{Escape}')
    expect(toggleDropdown).toHaveBeenCalledWith(false)
  })

  it('marks unread item as read and navigates to ticket detail when clicked', async () => {
    const markAsRead = vi.fn().mockResolvedValue({})
    const toggleDropdown = vi.fn()
    const item = {
      id: 'notif-123',
      ticketId: 88,
      title: 'Ticket Resolved',
      message: 'Done',
      read: false,
      readAt: null,
    }

    useNotifications.mockReturnValue({
      ...defaultHookReturn,
      isOpen: true,
      notifications: [item],
      markAsRead,
      toggleDropdown,
    })

    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    const notifItem = screen.getByRole('button', {
      name: /Ticket Resolved - Chưa đọc/i,
    })
    await user.click(notifItem)

    expect(markAsRead).toHaveBeenCalledWith('notif-123')
    expect(mockNavigate).toHaveBeenCalledWith('/tickets/88')
    expect(toggleDropdown).toHaveBeenCalledWith(false)
  })

  it('navigates without calling markAsRead when item is already read', async () => {
    const markAsRead = vi.fn()
    const toggleDropdown = vi.fn()
    const item = {
      id: 'notif-already-read',
      ticketId: 99,
      title: 'Ticket Assigned',
      message: 'Assigned',
      read: true,
      readAt: '2026-10-30T10:00:00Z',
    }

    useNotifications.mockReturnValue({
      ...defaultHookReturn,
      isOpen: true,
      notifications: [item],
      markAsRead,
      toggleDropdown,
    })

    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    )

    const notifItem = screen.getByRole('button', {
      name: /Ticket Assigned - Đã đọc/i,
    })
    await user.click(notifItem)

    expect(markAsRead).not.toHaveBeenCalled()
    expect(mockNavigate).toHaveBeenCalledWith('/tickets/99')
    expect(toggleDropdown).toHaveBeenCalledWith(false)
  })
})

