import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { NotificationItem } from './NotificationItem'

describe('NotificationItem component', () => {
  const unreadItem = {
    id: '672000000000000000000001',
    ticketId: 105,
    title: 'Phân công phiếu yêu cầu',
    message: 'Bạn được phân công xử lý phiếu #105: Đèn hỏng.',
    createdAt: '2026-10-30T10:15:00Z',
    read: false,
    readAt: null,
  }

  const readItem = {
    id: '672000000000000000000002',
    ticketId: 202,
    title: 'Phiếu đã được giải quyết',
    message: 'Phiếu #202 của bạn đã được giải quyết bởi kỹ thuật viên.',
    createdAt: '2026-10-30T11:00:00Z',
    read: true,
    readAt: '2026-10-30T11:30:00Z',
  }

  it('renders unread notification with title, plain-text message, time, and unread badge', () => {
    const onSelect = vi.fn()
    render(<NotificationItem item={unreadItem} onSelect={onSelect} />)

    expect(screen.getByText('Phân công phiếu yêu cầu')).toBeInTheDocument()
    expect(
      screen.getByText('Bạn được phân công xử lý phiếu #105: Đèn hỏng.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Mới')).toBeInTheDocument()
    expect(screen.getByText('Xem phiếu #105')).toBeInTheDocument()
    expect(screen.queryByText('Đã đọc')).not.toBeInTheDocument()
  })

  it('renders read notification with read status and no unread badge', () => {
    const onSelect = vi.fn()
    render(<NotificationItem item={readItem} onSelect={onSelect} />)

    expect(screen.getByText('Phiếu đã được giải quyết')).toBeInTheDocument()
    expect(screen.getByText('Đã đọc')).toBeInTheDocument()
    expect(screen.queryByText('Mới')).not.toBeInTheDocument()
  })

  it('escapes and treats HTML in message strictly as plain text', () => {
    const rawHtmlItem = {
      ...unreadItem,
      message: '<script>alert("xss")</script><b>Bold Text</b>',
    }
    render(<NotificationItem item={rawHtmlItem} onSelect={vi.fn()} />)

    // The literal tags should be present in text content, not parsed into HTML elements
    expect(
      screen.getByText('<script>alert("xss")</script><b>Bold Text</b>'),
    ).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
  })

  it('calls onSelect when clicked', async () => {
    const onSelect = vi.fn()
    const user = userEvent.setup()
    render(<NotificationItem item={unreadItem} onSelect={onSelect} />)

    const itemEl = screen.getByRole('button', {
      name: /Phân công phiếu yêu cầu - Chưa đọc/i,
    })
    await user.click(itemEl)

    expect(onSelect).toHaveBeenCalledTimes(1)
    expect(onSelect).toHaveBeenCalledWith(unreadItem)
  })

  it('calls onSelect on Enter or Space key press', async () => {
    const onSelect = vi.fn()
    const user = userEvent.setup()
    render(<NotificationItem item={unreadItem} onSelect={onSelect} />)

    const itemEl = screen.getByRole('button', {
      name: /Phân công phiếu yêu cầu - Chưa đọc/i,
    })
    itemEl.focus()
    await user.keyboard('{Enter}')
    expect(onSelect).toHaveBeenCalledTimes(1)

    await user.keyboard(' ')
    expect(onSelect).toHaveBeenCalledTimes(2)
  })
})

