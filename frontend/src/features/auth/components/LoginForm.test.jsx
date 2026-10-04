import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { LoginForm } from './LoginForm'

describe('LoginForm', () => {
  it('renders email and password fields without a role selector', () => {
    render(<LoginForm onSubmit={vi.fn()} />)

    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Mật khẩu')).toBeInTheDocument()
    expect(screen.queryByRole('combobox')).toBeNull()
  })

  it('renders a submit button', () => {
    render(<LoginForm onSubmit={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeInTheDocument()
  })

  // Lưu ý: react-hook-form isSubmitting state khó test trực tiếp qua props
  // Nếu muốn test trạng thái disable, cần render form và click submit rồi đợi,
  // nhưng vì ở đây ta chỉ dùng LoginForm tĩnh, ta có thể tạm bỏ qua việc test 
  // isSubmitting cụ thể trừ khi pass nó vào như một prop bên ngoài.
})
