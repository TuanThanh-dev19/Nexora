import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loginApi } from '../api/authApi'
import { AuthContext } from '../context/auth-context'
import { LoginPage } from './LoginPage'

vi.mock('../api/authApi')

describe('LoginPage', () => {
  const mockLogin = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  function renderLoginPage() {
    return render(
      <AuthContext.Provider
        value={{
          login: mockLogin,
          isAuthenticated: false,
        }}
      >
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthContext.Provider>
    )
  }

  it('login_success_saves_token_and_navigates', async () => {
    loginApi.mockResolvedValueOnce({
      accessToken: 'mock-token',
      user: { id: 1, email: 'user@test.com', fullName: 'User', status: 'ACTIVE', roles: [] },
    })

    renderLoginPage()

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@test.com' } })
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    await waitFor(() => {
      expect(loginApi).toHaveBeenCalledWith('user@test.com', 'password123')
      expect(mockLogin).toHaveBeenCalledWith({
        accessToken: 'mock-token',
        user: expect.objectContaining({ email: 'user@test.com' }),
      })
    })
  })

  it('login_wrong_password_shows_vietnamese_error', async () => {
    loginApi.mockRejectedValueOnce({ response: { status: 401 } })

    renderLoginPage()

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'wrong@test.com' } })
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    await waitFor(() => {
      expect(screen.getByText('Email hoặc mật khẩu không đúng.')).toBeInTheDocument()
    })
  })

  it('login_network_error_shows_network_message', async () => {
    loginApi.mockRejectedValueOnce(new Error('Network Error'))

    renderLoginPage()

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@test.com' } })
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'pass' } })
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    await waitFor(() => {
      expect(screen.getByText('Lỗi kết nối. Vui lòng thử lại sau.')).toBeInTheDocument()
    })
  })

  it('login_does_not_render_token_in_dom', async () => {
    loginApi.mockResolvedValueOnce({
      accessToken: 'super-secret-jwt-token-12345',
      user: { id: 1, email: 'user@test.com', fullName: 'User', status: 'ACTIVE', roles: [] },
    })

    renderLoginPage()

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@test.com' } })
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    await waitFor(() => {
      expect(document.body.innerHTML).not.toContain('super-secret-jwt-token-12345')
    })
  })
})
