import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { expect, it, vi } from 'vitest'
import { AuthContext } from '../../features/auth/context/auth-context'
import { AppLayout } from './AppLayout'

it('renders mixed-role navigation, current route, keyboard menu, account and logout', async () => {
  const logout = vi.fn()
  render(
    <AuthContext.Provider
      value={{
        user: {
          fullName: 'Test Admin',
          roles: ['ADMIN', 'REQUESTER', 'TECHNICIAN'],
        },
        logout,
      }}
    >
      <MemoryRouter initialEntries={['/tickets/new']}>
        <AppLayout />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
  expect(screen.getByRole('link', { name: 'Tạo phiếu' })).toHaveAttribute(
    'aria-current',
    'page',
  )
  expect(
    screen.getByRole('link', { name: 'Phiếu yêu cầu' }),
  ).not.toHaveAttribute('aria-current')
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
  render(
    <AuthContext.Provider value={{ user: null, isChecking: true }}>
      <MemoryRouter>
        <AppLayout />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
  expect(
    screen.getByText('Đang kiểm tra phiên đăng nhập...'),
  ).toBeInTheDocument()
  expect(
    screen.queryByRole('link', { name: 'Tổng quan' }),
  ).not.toBeInTheDocument()
  expect(
    screen.queryByRole('link', { name: 'Đăng nhập' }),
  ).not.toBeInTheDocument()
})

it('announces the mobile menu state and collapses after selecting a page', async () => {
  render(
    <AuthContext.Provider
      value={{ user: { fullName: 'Requester', roles: ['REQUESTER'] } }}
    >
      <MemoryRouter>
        <AppLayout />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
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
  render(
    <AuthContext.Provider value={{ user: null }}>
      <MemoryRouter initialEntries={['/login']}>
        <AppLayout />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
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
