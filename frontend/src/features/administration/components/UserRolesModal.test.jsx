import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { UserRolesModal } from './UserRolesModal'

describe('UserRolesModal', () => {
  const mockUser = {
    id: 2,
    fullName: 'Đỗ Thị D',
    email: 'd@campus.edu',
    roles: ['REQUESTER'],
  }

  it('renders user details and existing role selections', () => {
    render(
      <UserRolesModal
        show={true}
        user={mockUser}
        onHide={vi.fn()}
        onConfirm={vi.fn()}
      />,
    )

    expect(screen.getByText('Đỗ Thị D')).toBeInTheDocument()
    expect(screen.getByText('d@campus.edu')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Requester' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Admin' })).not.toBeChecked()
  })


  it('updates selection and submits with valid roles', () => {
    const handleConfirm = vi.fn()
    render(
      <UserRolesModal
        show={true}
        user={mockUser}
        onHide={vi.fn()}
        onConfirm={handleConfirm}
      />,
    )

    // Check Manager and Admin
    fireEvent.click(screen.getByRole('checkbox', { name: 'Manager' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Admin' }))

    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi vai trò' }))

    expect(handleConfirm).toHaveBeenCalledWith({
      roles: expect.arrayContaining(['REQUESTER', 'MANAGER', 'ADMIN']),
    })
  })

  it('prevents saving when all roles are unselected', () => {
    const handleConfirm = vi.fn()
    render(
      <UserRolesModal
        show={true}
        user={mockUser}
        onHide={vi.fn()}
        onConfirm={handleConfirm}
      />,
    )

    // Uncheck Requester
    fireEvent.click(screen.getByRole('checkbox', { name: 'Requester' }))

    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi vai trò' }))

    expect(
      screen.getByText('Người dùng phải có ít nhất một vai trò được chỉ định.'),
    ).toBeInTheDocument()
    expect(handleConfirm).not.toHaveBeenCalled()
  })
})
