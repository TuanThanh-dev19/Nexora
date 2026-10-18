import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { technicianApi } from '../api/technicianApi'
import { AuthContext } from '../../auth/context/auth-context'
import { TechnicianListPage } from './TechnicianListPage'

vi.mock('../api/technicianApi', () => ({
  technicianApi: {
    list: vi.fn(),
  },
}))

vi.mock('../../../shared/config/env', () => ({
  isTechnicianMockEnabled: vi.fn(() => false),
}))

const mockTechniciansData = {
  content: [
    {
      technicianId: 3,
      fullName: 'Nguyễn Kỹ Thuật',
      email: 'tech1@nexora.campus',
      status: 'ACTIVE',
      available: true,
      maxActiveTickets: 5,
      version: 0,
    },
    {
      technicianId: 7,
      fullName: 'Hoàng Đa Năng',
      email: 'tech.manager@nexora.campus',
      status: 'ACTIVE',
      available: false,
      maxActiveTickets: 3,
      version: 0,
    },
  ],
  totalElements: 2,
  totalPages: 1,
  page: 0,
  size: 20,
}

describe('TechnicianListPage', () => {
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
            user: { id: 2, fullName: 'Manager User', roles: ['MANAGER'] },
          }}
        >
          <MemoryRouter>
            <TechnicianListPage />
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )
  }

  it('renders heading, helper text, and technician table data', async () => {
    technicianApi.list.mockResolvedValueOnce(mockTechniciansData)

    renderPage()

    expect(screen.getByRole('heading', { name: 'Kỹ thuật viên' })).toBeInTheDocument()
    expect(screen.getByText(/Danh sách phục vụ cấu hình lực lượng kỹ thuật viên/)).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('Nguyễn Kỹ Thuật')).toBeInTheDocument()
      expect(screen.getByText('tech1@nexora.campus')).toBeInTheDocument()
      expect(screen.getByText('Hoàng Đa Năng')).toBeInTheDocument()
      expect(screen.getByText('tech.manager@nexora.campus')).toBeInTheDocument()
      expect(screen.getByText('2 kỹ thuật viên')).toBeInTheDocument()
    })

    // Badges
    expect(screen.getAllByText('Sẵn sàng').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Không sẵn sàng').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Hoạt động').length).toBe(2)

    // Capacity
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()

    // Action buttons
    const actionButtons = screen.getAllByRole('button', { name: /Xem \/ Cấu hình/ })
    expect(actionButtons).toHaveLength(2)
    expect(actionButtons[0]).toHaveAttribute('href', '/technicians/3')
  })

  it('filters by query and availability, and triggers API call', async () => {
    technicianApi.list.mockResolvedValue(mockTechniciansData)

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Nguyễn Kỹ Thuật')).toBeInTheDocument()
    })

    // Enter search query
    const searchInput = screen.getByLabelText(/Tìm kiếm kỹ thuật viên/)
    fireEvent.change(searchInput, { target: { value: 'tech1' } })

    // Change availability filter
    const availSelect = screen.getByLabelText(/Trạng thái sẵn sàng/)
    fireEvent.change(availSelect, { target: { value: 'true' } })

    await waitFor(() => {
      expect(technicianApi.list).toHaveBeenCalledWith(
        expect.objectContaining({
          query: 'tech1',
          available: 'true',
        }),
        expect.anything(),
      )
    })

    // Reset button appears
    const resetButton = screen.getByRole('button', { name: /Đặt lại/ })
    fireEvent.click(resetButton)

    await waitFor(() => {
      expect(searchInput).toHaveValue('')
      expect(availSelect).toHaveValue('')
    })
  })

  it('renders empty state when no technicians match filter', async () => {
    technicianApi.list.mockResolvedValueOnce({
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 20,
    })

    renderPage()

    await waitFor(() => {
      expect(
        screen.getByText('Không có kỹ thuật viên nào phù hợp với bộ lọc.'),
      ).toBeInTheDocument()
    })
  })

  it('renders error state with retry button on failure', async () => {
    technicianApi.list.mockRejectedValueOnce(new Error('Network error'))

    renderPage()

    await waitFor(() => {
      expect(screen.getByText(/Không thể kết nối đến máy chủ/)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Thử lại/ })).toBeInTheDocument()
    })

    // Clicking retry calls list again
    technicianApi.list.mockResolvedValueOnce(mockTechniciansData)
    fireEvent.click(screen.getByRole('button', { name: /Thử lại/ }))

    await waitFor(() => {
      expect(screen.getByText('Nguyễn Kỹ Thuật')).toBeInTheDocument()
    })
  })

  it('paginates using zero-based page numbers and resets page on filter change', async () => {
    const multiPageData = {
      content: [
        {
          technicianId: 3,
          fullName: 'Nguyễn Kỹ Thuật',
          email: 'tech1@nexora.campus',
          status: 'ACTIVE',
          available: true,
          maxActiveTickets: 5,
          version: 0,
        },
      ],
      totalElements: 40,
      totalPages: 2,
      page: 0,
      size: 20,
    }
    technicianApi.list.mockResolvedValue(multiPageData)

    renderPage()

    await waitFor(() => {
      expect(screen.getByText(/Trang 1\/2/)).toBeInTheDocument()
    })

    // Click next page
    const nextButton = screen.getByRole('button', { name: /Trang sau/ })
    expect(nextButton).not.toBeDisabled()
    fireEvent.click(nextButton)

    await waitFor(() => {
      expect(technicianApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ page: 1 }),
        expect.anything(),
      )
    })

    // Changing availability filter resets page to 0
    const availSelect = screen.getByLabelText(/Trạng thái sẵn sàng/)
    fireEvent.change(availSelect, { target: { value: 'false' } })

    await waitFor(() => {
      expect(technicianApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ page: 0, available: 'false' }),
        expect.anything(),
      )
    })
  })

  it('displays user status and technician availability separately with appropriate badges', async () => {
    technicianApi.list.mockResolvedValueOnce({
      content: [
        {
          technicianId: 10,
          fullName: 'Trần Bị Khoá',
          email: 'locked@nexora.campus',
          status: 'LOCKED',
          available: true,
          maxActiveTickets: 5,
          version: 1,
        },
        {
          technicianId: 11,
          fullName: 'Lê Vô Hiệu',
          email: 'disabled@nexora.campus',
          status: 'DISABLED',
          available: false,
          maxActiveTickets: 2,
          version: 1,
        },
      ],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Trần Bị Khoá')).toBeInTheDocument()
      expect(screen.getByText('Lê Vô Hiệu')).toBeInTheDocument()
    })

    // Status badges
    expect(document.querySelector('[data-status="LOCKED"]')).toHaveTextContent('Tạm khóa')
    expect(document.querySelector('[data-status="DISABLED"]')).toHaveTextContent('Vô hiệu hóa')

    // Availability badges
    expect(document.querySelector('[data-available="true"]')).toHaveTextContent('Sẵn sàng')
    expect(document.querySelector('[data-available="false"]')).toHaveTextContent('Không sẵn sàng')
  })
})

