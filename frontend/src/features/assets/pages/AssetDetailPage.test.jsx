import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { assetApi } from '../api/assetApi'
import { locationApi } from '../../locations/api/locationApi'
import { equipmentTypeApi } from '../../equipmentTypes/api/equipmentTypeApi'
import { AssetDetailPage } from './AssetDetailPage'

vi.mock('../api/assetApi', () => ({
  assetApi: {
    getById: vi.fn(),
    getStatusHistory: vi.fn(),
    update: vi.fn(),
    changeStatus: vi.fn(),
  },
}))

vi.mock('../../locations/api/locationApi', () => ({
  locationApi: {
    list: vi.fn(),
    getById: vi.fn(),
  },
}))

vi.mock('../../equipmentTypes/api/equipmentTypeApi', () => ({
  equipmentTypeApi: {
    list: vi.fn(),
  },
}))

const mockAsset = {
  id: 1,
  assetCode: 'AST-001',
  serialNumber: 'SN-10001',
  equipmentType: { id: 1, code: 'LAPTOP', name: 'Máy tính xách tay' },
  location: { id: 10, code: 'P301', name: 'Phòng 301' },
  status: 'ACTIVE',
  description: 'Laptop phục vụ giảng dạy tại P301',
  purchasedAt: '2026-01-15',
  warrantyUntil: '2027-01-15',
  version: 0,
  createdAt: '2026-01-15T08:00:00Z',
  updatedAt: '2026-01-15T08:00:00Z',
}

const mockHistory = [
  {
    id: 101,
    assetId: 1,
    fromStatus: null,
    toStatus: 'ACTIVE',
    reason: 'Initial asset registration',
    changedBy: { id: 99, fullName: 'Admin User', email: 'admin@nexora.local' },
    ticketId: null,
    changedAt: '2026-01-15T08:00:00Z',
  },
]

describe('AssetDetailPage', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })

    equipmentTypeApi.list.mockResolvedValue([
      { id: 1, code: 'LAPTOP', name: 'Máy tính xách tay' },
    ])
    locationApi.list.mockResolvedValue([
      { id: 10, code: 'P301', name: 'Phòng 301', active: true, type: 'ROOM' },
    ])
    locationApi.getById.mockResolvedValue({
      id: 10,
      code: 'P301',
      name: 'Phòng 301',
      active: true,
      type: 'ROOM',
    })

    assetApi.getById.mockResolvedValue(mockAsset)
    assetApi.getStatusHistory.mockResolvedValue(mockHistory)
  })

  const renderComponent = (assetId = '1') =>
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[`/assets/${assetId}`]}>
          <Routes>
            <Route path="/assets/:assetId" element={<AssetDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

  it('renders invalid ID error when assetId is not a positive integer', () => {
    renderComponent('invalid-id')

    expect(
      screen.getByText(
        'Mã định danh tài sản không hợp lệ. Vui lòng quay lại danh sách.',
      ),
    ).toBeInTheDocument()
    expect(assetApi.getById).not.toHaveBeenCalled()
  })

  it('renders error state and allows retrying when API fails', async () => {
    const error = new Error('Asset not found')
    error.response = {
      status: 404,
      data: { detail: 'Không tìm thấy tài sản.' },
    }
    assetApi.getById.mockRejectedValueOnce(error)

    renderComponent('999')

    await waitFor(() => {
      expect(screen.getByText('Không tìm thấy tài sản.')).toBeInTheDocument()
    })

    const retryButton = screen.getByRole('button', { name: 'Thử lại' })
    assetApi.getById.mockResolvedValueOnce(mockAsset)

    fireEvent.click(retryButton)

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })
  })

  it('renders asset metadata, description, and status badge', async () => {
    renderComponent('1')

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })

    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByText('Phiên bản 0')).toBeInTheDocument()
    expect(screen.getByText('Máy tính xách tay (LAPTOP)')).toBeInTheDocument()
    expect(screen.getByText('Phòng 301 (P301)')).toBeInTheDocument()
    expect(screen.getByText('SN-10001')).toBeInTheDocument()
    expect(screen.getByText('2026-01-15')).toBeInTheDocument()
    expect(screen.getByText('2027-01-15')).toBeInTheDocument()
    expect(
      screen.getByText('Laptop phục vụ giảng dạy tại P301'),
    ).toBeInTheDocument()
  })

  it('renders status history timeline with actor and timestamp', async () => {
    renderComponent('1')

    await waitFor(() => {
      expect(screen.getByText('Khởi tạo tài sản → Active')).toBeInTheDocument()
    })

    expect(screen.getByText(/Admin User/)).toBeInTheDocument()
    expect(screen.getByText('Initial asset registration')).toBeInTheDocument()
  })

  it('renders empty state when status history is empty', async () => {
    assetApi.getStatusHistory.mockResolvedValueOnce([])

    renderComponent('1')

    await waitFor(() => {
      expect(
        screen.getByText('Chưa có lịch sử trạng thái.'),
      ).toBeInTheDocument()
    })
  })

  it('opens Edit modal, updates description, and refetches detail', async () => {
    assetApi.update.mockResolvedValueOnce({
      ...mockAsset,
      description: 'Mô tả đã được cập nhật',
      version: 1,
    })

    renderComponent('1')

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })

    const editBtn = screen.getByRole('button', { name: /Chỉnh sửa tài sản/ })
    fireEvent.click(editBtn)

    expect(
      screen.getByRole('heading', { name: /Chỉnh sửa tài sản: AST-001/ }),
    ).toBeInTheDocument()

    const descInput = screen.getByLabelText('Mô tả tài sản')
    fireEvent.change(descInput, { target: { value: 'Mô tả đã được cập nhật' } })

    fireEvent.click(screen.getByRole('button', { name: 'Cập nhật' }))

    await waitFor(() => {
      expect(assetApi.update).toHaveBeenCalledWith(
        1,
        expect.objectContaining({
          description: 'Mô tả đã được cập nhật',
          version: 0,
        }),
      )
    })

    await waitFor(() => {
      expect(
        screen.getByText(/Đã cập nhật tài sản "AST-001" thành công/),
      ).toBeInTheDocument()
    })
  })

  it('opens Status modal, changes status, and updates status badge', async () => {
    assetApi.changeStatus.mockResolvedValueOnce({
      ...mockAsset,
      status: 'UNDER_MAINTENANCE',
      version: 1,
    })

    renderComponent('1')

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })

    const statusBtn = screen.getByRole('button', { name: /Đổi trạng thái/ })
    fireEvent.click(statusBtn)

    expect(
      screen.getByRole('heading', { name: 'Chuyển trạng thái tài sản' }),
    ).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Lý do thay đổi/), {
      target: { value: 'Bảo trì nguồn' },
    })

    fireEvent.click(
      screen.getByRole('button', { name: 'Xác nhận đổi trạng thái' }),
    )

    await waitFor(() => {
      expect(assetApi.changeStatus).toHaveBeenCalledWith(1, {
        targetStatus: 'UNDER_MAINTENANCE',
        reason: 'Bảo trì nguồn',
        version: 0,
      })
    })

    await waitFor(() => {
      expect(
        screen.getByText(
          /Đã chuyển trạng thái tài sản "AST-001" sang "Under maintenance"/,
        ),
      ).toBeInTheDocument()
    })
  })

  it('handles 409 conflict during edit and offers reloading latest data', async () => {
    const conflictError = new Error('Conflict')
    conflictError.response = {
      status: 409,
      data: {
        title: 'Concurrent update conflict',
        detail: 'The resource was changed by another request.',
      },
    }

    assetApi.update.mockRejectedValueOnce(conflictError)
    assetApi.getById.mockResolvedValueOnce({
      ...mockAsset,
      description: 'Phiên bản mới từ người khác',
      version: 2,
    })

    renderComponent('1')

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /Chỉnh sửa tài sản/ }))

    fireEvent.click(screen.getByRole('button', { name: 'Cập nhật' }))

    await waitFor(() => {
      expect(screen.getByText(/Xung đột phiên bản/)).toBeInTheDocument()
    })

    const reloadButton = screen.getByRole('button', {
      name: 'Tải lại dữ liệu mới nhất',
    })
    fireEvent.click(reloadButton)

    await waitFor(() => {
      expect(assetApi.getById).toHaveBeenCalledWith(1)
    })
  })
})
