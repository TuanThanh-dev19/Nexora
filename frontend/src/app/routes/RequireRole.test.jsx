import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { RequireRole } from './RequireRole'
import { AuthContext } from '../../features/auth/context/auth-context'

describe('RequireRole', () => {
  function renderWithAuth(authValue, requiredRoles) {
    return render(
      <AuthContext.Provider value={authValue}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route
              path="/unauthorized"
              element={<div data-testid="unauthorized-page">Unauthorized</div>}
            />
            <Route element={<RequireRole roles={requiredRoles} />}>
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
    renderWithAuth({ isChecking: true }, ['ADMIN'])
    expect(
      screen.getByText('Đang kiểm tra phiên đăng nhập...'),
    ).toBeInTheDocument()
    expect(screen.queryByTestId('protected-content')).toBeNull()
    expect(screen.queryByTestId('unauthorized-page')).toBeNull()
  })

  it('shows 403 in place when role is missing', () => {
    const user = { roles: ['REQUESTER'] }
    renderWithAuth({ isChecking: false, user }, ['ADMIN'])
    expect(screen.getByText('403 — Từ chối truy cập')).toBeInTheDocument()
    expect(screen.queryByTestId('protected-content')).toBeNull()
  })

  it('shows 403 in place when user is null', () => {
    renderWithAuth({ isChecking: false, user: null }, ['ADMIN'])
    expect(screen.getByText('403 — Từ chối truy cập')).toBeInTheDocument()
    expect(screen.queryByTestId('protected-content')).toBeNull()
  })

  it('renders outlet when role matches', () => {
    const user = { roles: ['ADMIN', 'MANAGER'] }
    renderWithAuth({ isChecking: false, user }, ['ADMIN'])
    expect(screen.getByTestId('protected-content')).toBeInTheDocument()
    expect(screen.queryByTestId('unauthorized-page')).toBeNull()
  })
})
