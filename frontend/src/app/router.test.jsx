import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
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
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

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
    <QueryClientProvider client={queryClient}>
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
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

it.each([
  ['/assets', ['MANAGER'], 'Tài sản'],
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
  ['/administration/locations', ['ADMIN'], 'Quản lý địa điểm'],
  ['/administration/equipment-types', ['ADMIN'], 'Loại thiết bị'],
  ['/technicians', ['MANAGER'], 'Kỹ thuật viên'],
  ['/technicians/me', ['TECHNICIAN'], 'Hồ sơ kỹ thuật của tôi'],
])('renders full management UI for %s', (path, roles, title) => {
  open(path, roles)
  expect(screen.getByRole('heading', { name: title })).toBeInTheDocument()
  expect(screen.getByTestId('location')).toHaveTextContent(path)
})

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
