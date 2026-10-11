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
