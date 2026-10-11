import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { adminUserApi } from '../api/adminUserApi'
import { AuthContext } from '../../auth/context/auth-context'
import { UserDetailPage } from './UserDetailPage'

vi.mock('../api/adminUserApi', () => ({
  adminUserApi: {
    getById: vi.fn(),
    updateStatus: vi.fn(),
    updateRoles: vi.fn(),
  },
  formatAdminUserError: vi.fn((err) => err?.message || 'Error message'),
}))

vi.mock('../../../shared/config/env', () => ({
  isAdminUserMockEnabled: vi.fn(() => false),
}))

const mockUser = {
  id: 10,
  fullName: 'Phạm Chi Tiết',
  email: 'chitiet@campus.edu',
  status: 'ACTIVE',
  roles: ['MANAGER', 'TECHNICIAN'],
  createdAt: '2026-09-10T10:00:00Z',
  updatedAt: '2026-09-11T12:00:00Z',
  version: 2,
}

describe('UserDetailPage', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })
  })

  function renderPage(userId = '10') {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider
          value={{
            isAuthenticated: true,
            isChecking: false,
            user: { id: 1, fullName: 'Admin', roles: ['ADMIN'] },
          }}
        >
          <MemoryRouter initialEntries={[`/administration/users/${userId}`]}>
            <Routes>
              <Route path="/administration/users/:userId" element={<UserDetailPage />} />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )
  }

  it('renders user details, badges, and metadata', async () => {
    adminUserApi.getById.mockResolvedValueOnce(mockUser)

    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Phạm Chi Tiết' })).toBeInTheDocument()
      expect(screen.getByText('chitiet@campus.edu')).toBeInTheDocument()
      expect(screen.getAllByText(/#10/).length).toBeGreaterThan(0)
      expect(screen.getAllByText('Hoạt động').length).toBeGreaterThan(0)
      expect(screen.getAllByText('Manager').length).toBeGreaterThan(0)
      expect(screen.getAllByText('Technician').length).toBeGreaterThan(0)
    })
  })


  it('renders not found state on 404 error', async () => {
    const notFoundError = new Error('Not found')
    notFoundError.response = { status: 404 }
    adminUserApi.getById.mockRejectedValueOnce(notFoundError)

    renderPage('99')

    await waitFor(() => {
      expect(screen.getByText('Không tìm thấy người dùng')).toBeInTheDocument()
      expect(screen.getByText(/Tài khoản với mã #99 không tồn tại/)).toBeInTheDocument()
    })
  })

  it('opens manage roles modal', async () => {
    adminUserApi.getById.mockResolvedValueOnce(mockUser)

    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Phạm Chi Tiết' })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /Quản lý vai trò/ }))

    expect(screen.getByRole('heading', { name: 'Phân quyền vai trò người dùng' })).toBeInTheDocument()
  })
})
