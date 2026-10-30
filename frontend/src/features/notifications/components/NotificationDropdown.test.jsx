import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NotificationDropdown } from './NotificationDropdown'

describe('NotificationDropdown component', () => {
  const defaultProps = {
    notifications: [],
    unreadCount: 0,
    isLoading: false,
    isFetching: false,
    error: null,
    actionError: null,
    unreadOnly: false,
    onFilterChange: vi.fn(),
    onRefresh: vi.fn(),
    pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
    onNextPage: vi.fn(),
    onPrevPage: vi.fn(),
    onSelectItem: vi.fn(),
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders header with title and unread count', () => {
    render(<NotificationDropdown {...defaultProps} unreadCount={4} />)

    expect(screen.getByText('Thông báo')).toBeInTheDocument()
    expect(screen.getByText('(4)')).toBeInTheDocument()
  })

  it('triggers onFilterChange when switching filter buttons', async () => {
    const onFilterChange = vi.fn()
    const user = userEvent.setup()
    render(
      <NotificationDropdown
        {...defaultProps}
        unreadCount={2}
        onFilterChange={onFilterChange}
      />,
    )

    const unreadFilterBtn = screen.getByRole('button', { name: /Chưa đọc \(2\)/i })
    await user.click(unreadFilterBtn)
    expect(onFilterChange).toHaveBeenCalledWith(true)

    const allFilterBtn = screen.getByRole('button', { name: 'Tất cả' })
    await user.click(allFilterBtn)
    expect(onFilterChange).toHaveBeenCalledWith(false)
  })

  it('triggers onRefresh when clicking refresh button', async () => {
    const onRefresh = vi.fn()
    const user = userEvent.setup()
    render(<NotificationDropdown {...defaultProps} onRefresh={onRefresh} />)

    const refreshBtn = screen.getByRole('button', { name: 'Làm mới thông báo' })
    await user.click(refreshBtn)
    expect(onRefresh).toHaveBeenCalledTimes(1)
  })

  it('renders loading spinner when isLoading is true', () => {
    render(<NotificationDropdown {...defaultProps} isLoading={true} />)

    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.getByText('Đang tải thông báo...')).toBeInTheDocument()
  })

  it('renders error state and allows retry', async () => {
    const onRefresh = vi.fn()
    const user = userEvent.setup()
    render(
      <NotificationDropdown
        {...defaultProps}
        error={new Error('Network error')}
        onRefresh={onRefresh}
      />,
    )

    expect(screen.getByText('Không thể tải thông báo.')).toBeInTheDocument()
    const retryBtn = screen.getByRole('button', { name: 'Thử lại' })
    await user.click(retryBtn)
    expect(onRefresh).toHaveBeenCalledTimes(1)
  })

  it('renders empty state when there are no notifications', () => {
    render(<NotificationDropdown {...defaultProps} notifications={[]} />)

    expect(screen.getByText('Bạn chưa có thông báo nào.')).toBeInTheDocument()
  })

  it('renders empty state for unread filter when there are no unread notifications', () => {
    render(
      <NotificationDropdown
        {...defaultProps}
        notifications={[]}
        unreadOnly={true}
      />,
    )

    expect(
      screen.getByText('Không có thông báo chưa đọc nào.'),
    ).toBeInTheDocument()
  })

  it('renders list of notification items and handles selection', async () => {
    const onSelectItem = vi.fn()
    const user = userEvent.setup()
    const items = [
      {
        id: '1',
        title: 'Phân công 1',
        message: 'Nội dung 1',
        ticketId: 10,
        createdAt: '2026-10-30T10:00:00Z',
        read: false,
      },
      {
        id: '2',
        title: 'Giải quyết 2',
        message: 'Nội dung 2',
        ticketId: 20,
        createdAt: '2026-10-30T11:00:00Z',
        read: true,
        readAt: '2026-10-30T11:30:00Z',
      },
    ]

    render(
      <NotificationDropdown
        {...defaultProps}
        notifications={items}
        onSelectItem={onSelectItem}
      />,
    )

    expect(screen.getByText('Phân công 1')).toBeInTheDocument()
    expect(screen.getByText('Giải quyết 2')).toBeInTheDocument()

    await user.click(screen.getByText('Phân công 1'))
    expect(onSelectItem).toHaveBeenCalledWith(items[0])
  })

  it('renders pagination controls and triggers page changes', async () => {
    const onNextPage = vi.fn()
    const onPrevPage = vi.fn()
    const user = userEvent.setup()

    render(
      <NotificationDropdown
        {...defaultProps}
        notifications={[{ id: '1', title: 'T1', message: 'M1' }]}
        pagination={{ page: 0, size: 20, totalElements: 30, totalPages: 2 }}
        onNextPage={onNextPage}
        onPrevPage={onPrevPage}
      />,
    )

    expect(screen.getByText('Trang 1 / 2')).toBeInTheDocument()
    const prevBtn = screen.getByRole('button', { name: 'Trang trước' })
    const nextBtn = screen.getByRole('button', { name: 'Trang sau' })

    expect(prevBtn).toBeDisabled()
    expect(nextBtn).toBeEnabled()

    await user.click(nextBtn)
    expect(onNextPage).toHaveBeenCalledTimes(1)
  })

  it('displays actionError banner if present', () => {
    render(
      <NotificationDropdown
        {...defaultProps}
        actionError="Lỗi khi đánh dấu thông báo."
      />,
    )

    expect(screen.getByText('Lỗi khi đánh dấu thông báo.')).toBeInTheDocument()
  })
})

