import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { UserStatusModal } from './UserStatusModal'

describe('UserStatusModal', () => {
  const mockUser = {
    id: 1,
    fullName: 'Lê Văn C',
    email: 'c@campus.edu',
    status: 'ACTIVE',
    version: 3,
  }

  it('renders confirmation text, user info, and status descriptions', () => {
    render(
      <UserStatusModal
        show={true}
        user={mockUser}
        newStatus="LOCKED"
        onHide={vi.fn()}
        onConfirm={vi.fn()}
      />,
    )

    expect(screen.getByText(/Xác nhận đổi trạng thái tài khoản/)).toBeInTheDocument()
    expect(screen.getByText('Lê Văn C')).toBeInTheDocument()
    expect(screen.getByText('c@campus.edu')).toBeInTheDocument()
    expect(screen.getByText('Khóa tài khoản')).toBeInTheDocument()
  })

  it('calls onConfirm with target status and optimistic version', () => {
    const handleConfirm = vi.fn()
    render(
      <UserStatusModal
        show={true}
        user={mockUser}
        newStatus="DISABLED"
        onHide={vi.fn()}
        onConfirm={handleConfirm}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Vô hiệu hóa tài khoản' }))

    expect(handleConfirm).toHaveBeenCalledWith({
      status: 'DISABLED',
      version: 3,
    })
  })

  it('displays error alert when mutation fails', () => {
    const error = { response: { status: 409, data: { detail: 'Concurrent update conflict' } } }
    render(
      <UserStatusModal
        show={true}
        user={mockUser}
        newStatus="LOCKED"
        onHide={vi.fn()}
        onConfirm={vi.fn()}
        error={error}
      />,
    )

    expect(screen.getByText(/Dữ liệu người dùng đã bị thay đổi bởi thao tác khác/)).toBeInTheDocument()
  })
})
