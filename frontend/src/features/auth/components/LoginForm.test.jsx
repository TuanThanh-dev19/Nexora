import { fireEvent, render, screen } from '@testing-library/react'
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

  it('toggles password visibility when clicking toggle button', async () => {
    render(<LoginForm onSubmit={vi.fn()} />)
    const passwordInput = screen.getByLabelText('Mật khẩu')
    const toggleButton = screen.getByRole('button', { name: 'Hiện mật khẩu' })

    expect(passwordInput).toHaveAttribute('type', 'password')
    expect(toggleButton).toBeInTheDocument()

    // Toggle on
    fireEvent.click(toggleButton)
    expect(passwordInput).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ẩn mật khẩu' })).toBeInTheDocument()

    // Toggle off
    fireEvent.click(screen.getByRole('button', { name: 'Ẩn mật khẩu' }))
    expect(passwordInput).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Hiện mật khẩu' })).toBeInTheDocument()
  })
})

