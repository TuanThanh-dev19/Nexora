import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { adminUserApi } from '../api/adminUserApi'
import { AuthContext } from '../../auth/context/auth-context'
import { UserListPage } from './UserListPage'

vi.mock('../api/adminUserApi', () => ({
  adminUserApi: {
    list: vi.fn(),
    create: vi.fn(),
    updateStatus: vi.fn(),
    updateRoles: vi.fn(),
  },
  formatAdminUserError: vi.fn((err) => err?.message || 'Error occurred'),
}))

vi.mock('../../../shared/config/env', () => ({
  isAdminUserMockEnabled: vi.fn(() => false),
}))

const mockUsersData = {
  content: [
    {
      id: 1,
      fullName: 'Trần Quản Trị',
      email: 'admin@campus.edu',
      status: 'ACTIVE',
      roles: ['ADMIN'],
      createdAt: '2026-09-01T08:00:00Z',
      version: 0,
    },
    {
      id: 2,
      fullName: 'Lê Kỹ Thuật',
      email: 'tech@campus.edu',
      status: 'LOCKED',
      roles: ['TECHNICIAN'],
      createdAt: '2026-09-02T08:00:00Z',
      version: 1,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
}

describe('UserListPage', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })
  })

  function renderPage() {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider
          value={{
            isAuthenticated: true,
            isChecking: false,
            user: { id: 1, fullName: 'Admin', roles: ['ADMIN'] },
          }}
        >
          <MemoryRouter>
            <UserListPage />
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )
  }

  it('renders heading, create action, and table data', async () => {
    adminUserApi.list.mockResolvedValueOnce(mockUsersData)

    renderPage()

    expect(screen.getByRole('heading', { name: 'Quản lý người dùng' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Tạo người dùng/ })).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('Trần Quản Trị')).toBeInTheDocument()
      expect(screen.getByText('admin@campus.edu')).toBeInTheDocument()
      expect(screen.getByText('Lê Kỹ Thuật')).toBeInTheDocument()
      expect(screen.getByText('tech@campus.edu')).toBeInTheDocument()
      expect(screen.getByText('2 tài khoản')).toBeInTheDocument()
    })

    // Badges
    expect(screen.getAllByText('Hoạt động').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Tạm khóa').length).toBeGreaterThan(0)
  })


  it('opens CreateUserModal when clicking Create User button', async () => {
    adminUserApi.list.mockResolvedValueOnce(mockUsersData)

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Trần Quản Trị')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /Tạo người dùng/ }))

    expect(screen.getByRole('heading', { name: 'Tạo tài khoản người dùng' })).toBeInTheDocument()
  })

  it('renders empty state when no users match filter', async () => {
    adminUserApi.list.mockResolvedValueOnce({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    renderPage()

    await waitFor(() => {
      expect(
        screen.getByText('Không tìm thấy người dùng nào phù hợp với bộ lọc.'),
      ).toBeInTheDocument()
    })
  })

  it('renders error state with retry button', async () => {
    adminUserApi.list.mockRejectedValueOnce(new Error('Server error'))

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Server error')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument()
    })
  })
})
