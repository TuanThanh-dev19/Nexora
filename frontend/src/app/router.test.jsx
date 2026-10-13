import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { expect, it, vi } from 'vitest'
import { AuthContext } from '../features/auth/context/auth-context'
import { appRoutes } from './router'

function LocationProbe() {
  const location = useLocation()
  return (
    <span data-testid="location">
      {location.pathname}
      {location.search}
    </span>
  )
}
function open(path, roles) {
  const routes = appRoutes.map((route) => ({
    ...route,
    element: (
      <>
        <LocationProbe />
        {route.element}
      </>
    ),
  }))
  render(
    <AuthContext.Provider
      value={{
        user: { fullName: 'Test', roles },
        isAuthenticated: true,
        isChecking: false,
        logout: vi.fn(),
      }}
    >
      <RouterProvider
        router={createMemoryRouter(routes, { initialEntries: [path] })}
      />
    </AuthContext.Provider>,
  )
}
it.each([
  ['/technicians/me', ['TECHNICIAN'], 'Hồ sơ kỹ thuật của tôi'],
  ['/technicians', ['MANAGER'], 'Kỹ thuật viên'],
  ['/assets', ['MANAGER'], 'Tài sản'],
  ['/administration/locations', ['ADMIN'], 'Địa điểm'],
  ['/administration/equipment-types', ['ADMIN'], 'Loại thiết bị'],
])(
  'reserves the correct guarded integration entry %s without claiming a finished UI',
  (path, roles, title) => {
    open(path, roles)
    expect(screen.getByRole('heading', { name: title })).toBeInTheDocument()
    expect(screen.getByText(/Đây không phải màn CRUD/)).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent(path)
  },
)
it.each([
  '/tickets/new',
  '/technicians',
  '/assets',
  '/administration/locations',
  '/administration/equipment-types',
])('denies unsupported roles in place for %s', (path) => {
  open(path, path === '/tickets/new' ? ['ADMIN'] : ['REQUESTER'])
  expect(screen.getByText('403 — Từ chối truy cập')).toBeInTheDocument()
  expect(screen.getByTestId('location')).toHaveTextContent(path)
})
it.each(['TECHNICIAN', 'MANAGER'])('denies Create Ticket to %s', (role) => {
  open('/tickets/new', [role])
  expect(screen.getByText('403 — Từ chối truy cập')).toBeInTheDocument()
})
it('preserves unknown route context on a real 404 page', () => {
  open('/missing-screen?source=test', ['ADMIN'])
  expect(
    screen.getByRole('heading', { name: 'Không tìm thấy trang' }),
  ).toBeInTheDocument()
  expect(screen.getByTestId('location')).toHaveTextContent(
    '/missing-screen?source=test',
  )
})
