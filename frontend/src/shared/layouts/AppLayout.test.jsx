import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthContext } from '../../features/auth/context/auth-context'
import { AppLayout } from './AppLayout'

function renderAppLayout(authValue, initialEntries = ['/']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={authValue}>
        <MemoryRouter initialEntries={initialEntries}>
          <AppLayout />
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

it('renders mixed-role navigation, current route, keyboard menu, account and logout', async () => {
  const logout = vi.fn()
  renderAppLayout(
    {
      user: {
        id: 1,
        fullName: 'Test Admin',
        roles: ['ADMIN', 'REQUESTER', 'TECHNICIAN'],
      },
      logout,
    },
    ['/tickets/new'],
  )
  expect(screen.getByRole('link', { name: 'Tạo phiếu' })).toHaveAttribute(
    'aria-current',
    'page',
  )
  expect(
    screen.getByRole('link', { name: 'Phiếu yêu cầu' }),
  ).not.toHaveAttribute('aria-current')
  expect(screen.getByRole('button', { name: /thông báo/i })).toBeInTheDocument()
  const user = userEvent.setup()
  const menu = screen.getByRole('button', { name: 'Quản lý' })
  menu.focus()
  await user.keyboard('{Enter}')
  expect(screen.getByRole('link', { name: 'Người dùng' })).toHaveAttribute(
    'href',
    '/administration/users',
  )
  expect(screen.getByRole('link', { name: 'Địa điểm' })).toHaveAttribute(
    'href',
    '/administration/locations',
  )
  expect(
    screen.getByRole('link', { name: 'Hồ sơ kỹ thuật của tôi' }),
  ).toBeInTheDocument()
  expect(screen.getByText('Test Admin')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Đăng xuất' }))
  expect(logout).toHaveBeenCalledOnce()
})

it('hides private navigation until session checking completes', () => {
  renderAppLayout({ user: null, isChecking: true })
  expect(
    screen.getByText('Đang kiểm tra phiên đăng nhập...'),
  ).toBeInTheDocument()
  expect(
    screen.queryByRole('link', { name: 'Tổng quan' }),
  ).not.toBeInTheDocument()
  expect(
    screen.queryByRole('link', { name: 'Đăng nhập' }),
  ).not.toBeInTheDocument()
  expect(
    screen.queryByRole('button', { name: /thông báo/i }),
  ).not.toBeInTheDocument()
})

it('announces the mobile menu state and collapses after selecting a page', async () => {
  renderAppLayout({
    user: { id: 2, fullName: 'Requester', roles: ['REQUESTER'] },
  })
  const user = userEvent.setup()
  const toggle = screen.getByRole('button', {
    name: 'Mở hoặc đóng điều hướng',
  })
  expect(toggle).toHaveAttribute('aria-expanded', 'false')
  toggle.focus()
  await user.keyboard('{Enter}')
  expect(toggle).toHaveAttribute('aria-expanded', 'true')
  await user.click(screen.getByRole('link', { name: 'Tổng quan' }))
  expect(toggle).toHaveAttribute('aria-expanded', 'false')
})

it('also collapses the anonymous menu when selecting login or the brand', async () => {
  renderAppLayout({ user: null }, ['/login'])
  const user = userEvent.setup()
  const toggle = screen.getByRole('button', {
    name: 'Mở hoặc đóng điều hướng',
  })
  for (const link of [
    screen.getByRole('link', { name: 'Đăng nhập' }),
    screen.getByRole('link', { name: /Nexora Logo/ }),
  ]) {
    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await user.click(link)
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
  }
})
