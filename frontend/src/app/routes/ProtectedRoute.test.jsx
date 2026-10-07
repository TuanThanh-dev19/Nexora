import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { ProtectedRoute } from './ProtectedRoute'
import { AuthContext } from '../../features/auth/context/auth-context'

describe('ProtectedRoute', () => {
  function LoginProbe() {
    const location = useLocation()
    return <div data-testid="login-page">{location.state?.from}</div>
  }
  function renderWithAuth(authValue, initialEntry = '/protected') {
    return render(
      <AuthContext.Provider value={authValue}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/login" element={<LoginProbe />} />
            <Route element={<ProtectedRoute />}>
              <Route
                path="/protected"
                element={
                  <div data-testid="protected-content">Protected Content</div>
                }
              />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )
  }

  it('shows spinner while checking session', () => {
    renderWithAuth({ isAuthenticated: false, isChecking: true })
    expect(
      screen.getByText('Đang kiểm tra phiên đăng nhập...'),
    ).toBeInTheDocument()
    expect(screen.queryByTestId('protected-content')).toBeNull()
    expect(screen.queryByTestId('login-page')).toBeNull()
  })

  it('redirects to login when not authenticated', () => {
    renderWithAuth({ isAuthenticated: false, isChecking: false })
    expect(screen.getByTestId('login-page')).toBeInTheDocument()
    expect(screen.queryByTestId('protected-content')).toBeNull()
  })

  it('renders outlet when authenticated', () => {
    renderWithAuth({ isAuthenticated: true, isChecking: false })
    expect(screen.getByTestId('protected-content')).toBeInTheDocument()
    expect(screen.queryByTestId('login-page')).toBeNull()
  })

  it('preserves filters in the login return destination', () => {
    renderWithAuth(
      { isAuthenticated: false, isChecking: false },
      '/protected?page=2&priority=HIGH',
    )
    expect(screen.getByTestId('login-page')).toHaveTextContent(
      '/protected?page=2&priority=HIGH',
    )
  })
})
