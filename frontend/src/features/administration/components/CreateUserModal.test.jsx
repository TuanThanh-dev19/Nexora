import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { CreateUserModal } from './CreateUserModal'

describe('CreateUserModal', () => {
  it('discards invalid draft state when Cancel closes the modal', async () => {
    const onHide = vi.fn()
    const props = { onHide, onSubmit: vi.fn() }
    const { rerender } = render(<CreateUserModal show {...props} />)
    fireEvent.click(screen.getByRole('checkbox', { name: 'Requester' }))
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
    await screen.findByText('Vui lòng chọn ít nhất một vai trò')
    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(onHide).toHaveBeenCalledOnce()
    rerender(<CreateUserModal show={false} {...props} />)
    rerender(<CreateUserModal show {...props} />)
    expect(screen.getByRole('checkbox', { name: 'Requester' })).toBeChecked()
    expect(
      screen.queryByText('Vui lòng chọn ít nhất một vai trò'),
    ).not.toBeInTheDocument()
    expect(screen.queryByText('Email không được để trống')).not.toBeInTheDocument()
    expect(props.onSubmit).not.toHaveBeenCalled()
  })

  it('renders required form fields and role checkboxes', () => {
    render(<CreateUserModal show={true} onHide={vi.fn()} onSubmit={vi.fn()} />)

    expect(screen.getByLabelText(/Họ và tên/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Email/)).toBeInTheDocument()
    expect(screen.getByLabelText(/^Mật khẩu khởi tạo/)).toBeInTheDocument()
    expect(screen.getByLabelText(/^Xác nhận mật khẩu/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Requester' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Technician' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Manager' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Admin' })).toBeInTheDocument()
  })

  it('validates empty inputs on submit', async () => {
    const handleSubmit = vi.fn()
    render(<CreateUserModal show={true} onHide={vi.fn()} onSubmit={handleSubmit} />)

    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))

    await waitFor(() => {
      expect(screen.getByText('Họ và tên không được để trống')).toBeInTheDocument()
      expect(screen.getByText('Email không được để trống')).toBeInTheDocument()
      expect(screen.getByText('Mật khẩu phải có ít nhất 6 ký tự')).toBeInTheDocument()
    })
    expect(handleSubmit).not.toHaveBeenCalled()
  })

  it('validates password mismatch', async () => {
    const handleSubmit = vi.fn()
    render(<CreateUserModal show={true} onHide={vi.fn()} onSubmit={handleSubmit} />)

    fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Nguyễn Văn A' } })
    fireEvent.change(screen.getByLabelText(/Email/), { target: { value: 'a@campus.edu' } })
    fireEvent.change(screen.getByLabelText(/^Mật khẩu khởi tạo/), { target: { value: 'password123' } })
    fireEvent.change(screen.getByLabelText(/^Xác nhận mật khẩu/), { target: { value: 'password999' } })

    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))

    await waitFor(() => {
      expect(screen.getByText('Mật khẩu xác nhận không khớp')).toBeInTheDocument()
    })
    expect(handleSubmit).not.toHaveBeenCalled()
  })

  it('submits valid data without sending confirmPassword', async () => {
    const handleSubmit = vi.fn()
    render(<CreateUserModal show={true} onHide={vi.fn()} onSubmit={handleSubmit} />)

    fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Trần Văn B' } })
    fireEvent.change(screen.getByLabelText(/Email/), { target: { value: 'b@campus.edu' } })
    fireEvent.change(screen.getByLabelText(/^Mật khẩu khởi tạo/), { target: { value: 'validpass123' } })
    fireEvent.change(screen.getByLabelText(/^Xác nhận mật khẩu/), { target: { value: 'validpass123' } })

    // Check Technician as well
    fireEvent.click(screen.getByRole('checkbox', { name: 'Technician' }))

    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))

    await waitFor(() => {
      expect(handleSubmit).toHaveBeenCalledWith({
        fullName: 'Trần Văn B',
        email: 'b@campus.edu',
        password: 'validpass123',
        roles: expect.arrayContaining(['REQUESTER', 'TECHNICIAN']),
      })
    })
  })

  it('displays server error when provided', () => {
    const serverError = { response: { status: 422, data: { detail: 'Email already in use' } } }
    render(
      <CreateUserModal
        show={true}
        onHide={vi.fn()}
        onSubmit={vi.fn()}
        serverError={serverError}
      />,
    )

    expect(screen.getByText('Email này đã được sử dụng trong hệ thống.')).toBeInTheDocument()
  })
})
