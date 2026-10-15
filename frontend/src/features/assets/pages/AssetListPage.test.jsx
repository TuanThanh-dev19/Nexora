import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { assetApi } from '../api/assetApi'
import { locationApi } from '../../locations/api/locationApi'
import { equipmentTypeApi } from '../../equipmentTypes/api/equipmentTypeApi'
import { AssetListPage } from './AssetListPage'

vi.mock('../api/assetApi', () => ({
  assetApi: {
    list: vi.fn(),
    getById: vi.fn(),
    create: vi.fn(),
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

const mockEquipmentTypes = [
  { id: 1, code: 'LAPTOP', name: 'Máy tính xách tay' },
  { id: 2, code: 'PROJECTOR', name: 'Máy chiếu' },
]

const mockLocations = [
  { id: 10, code: 'P301', name: 'Phòng 301', active: true, type: 'ROOM' },
  { id: 20, code: 'P302', name: 'Phòng 302', active: true, type: 'ROOM' },
]

const mockAssets = [
  {
    id: 1,
    assetCode: 'AST-001',
    serialNumber: 'SN-10001',
    equipmentType: { id: 1, code: 'LAPTOP', name: 'Máy tính xách tay' },
    location: { id: 10, code: 'P301', name: 'Phòng 301' },
    status: 'ACTIVE',
    description: 'Laptop giảng viên',
    purchasedAt: '2026-01-15',
    warrantyUntil: '2027-01-15',
    version: 0,
    createdAt: '2026-01-15T08:00:00Z',
    updatedAt: '2026-01-15T08:00:00Z',
  },
  {
    id: 2,
    assetCode: 'AST-002',
    serialNumber: 'SN-10002',
    equipmentType: { id: 2, code: 'PROJECTOR', name: 'Máy chiếu' },
    location: { id: 20, code: 'P302', name: 'Phòng 302' },
    status: 'UNDER_MAINTENANCE',
    description: 'Máy chiếu trần',
    purchasedAt: '2025-06-01',
    warrantyUntil: '2026-06-01',
    version: 1,
    createdAt: '2025-06-01T08:00:00Z',
    updatedAt: '2026-02-01T08:00:00Z',
  },
]

describe('AssetListPage', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })

    equipmentTypeApi.list.mockResolvedValue(mockEquipmentTypes)
    locationApi.list.mockResolvedValue(mockLocations)
    locationApi.getById.mockImplementation(
      async (id) => mockLocations.find((l) => l.id === Number(id)) ?? null,
    )

    assetApi.list.mockResolvedValue({
      content: mockAssets,
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    })
  })

  const renderComponent = (initialUrl = '/assets') =>
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialUrl]}>
          <AssetListPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

  it('renders page heading, create button, and asset table', async () => {
    renderComponent()

    expect(
      screen.getByRole('heading', { name: 'Danh sách tài sản' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /Thêm tài sản/ }),
    ).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
      expect(screen.getByText('AST-002')).toBeInTheDocument()
    })

    expect(screen.getByText('Máy tính xách tay')).toBeInTheDocument()
    expect(screen.getByText('Phòng 301')).toBeInTheDocument()
    expect(screen.getByText('SN-10001')).toBeInTheDocument()
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Under maintenance').length).toBeGreaterThan(0)
  })

  it('renders empty state when there are no assets', async () => {
    assetApi.list.mockResolvedValueOnce({
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 20,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Không có tài sản phù hợp.')).toBeInTheDocument()
    })
  })

  it('renders error state and retries on click', async () => {
    const netError = new Error('Network error')
    netError.response = { status: 500, data: { detail: 'Network error' } }
    assetApi.list.mockRejectedValueOnce(netError)

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText(/Network error/)).toBeInTheDocument()
    })

    const retryButton = screen.getByRole('button', { name: 'Thử lại' })
    assetApi.list.mockResolvedValueOnce({
      content: mockAssets,
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    })

    fireEvent.click(retryButton)

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })
  })

  it('filters by status and changes URL parameters', async () => {
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })

    const statusSelect = screen.getByLabelText('Trạng thái')
    fireEvent.change(statusSelect, { target: { value: 'UNDER_MAINTENANCE' } })

    await waitFor(() => {
      expect(assetApi.list).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'UNDER_MAINTENANCE',
          page: 0,
        }),
        expect.anything(),
      )
    })
  })

  it('opens Create modal, validates, creates an asset and refreshes list', async () => {
    assetApi.create.mockResolvedValueOnce({
      id: 3,
      assetCode: 'AST-003',
      version: 0,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })

    const addButton = screen.getByRole('button', { name: /Thêm tài sản/ })
    fireEvent.click(addButton)

    expect(
      screen.getByRole('heading', { name: 'Thêm tài sản mới' }),
    ).toBeInTheDocument()

    // Submit empty to check client validation
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài sản' }))

    expect(
      screen.getByText('Mã tài sản không được để trống.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Vui lòng chọn loại thiết bị.')).toBeInTheDocument()
    expect(
      screen.getByText('Vui lòng chọn địa điểm đặt tài sản.'),
    ).toBeInTheDocument()

    // Fill form
    fireEvent.change(screen.getByLabelText(/Mã tài sản/), {
      target: { value: 'AST-003' },
    })

    await waitFor(() => {
      expect(screen.getByLabelText(/Loại thiết bị/)).not.toBeDisabled()
    })
    fireEvent.change(screen.getByLabelText(/Loại thiết bị/), {
      target: { value: '1' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'Thay đổi địa điểm' }))
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Chọn địa điểm Phòng 301' }),
      ).toBeInTheDocument()
    })
    fireEvent.click(
      screen.getByRole('button', { name: 'Chọn địa điểm Phòng 301' }),
    )

    fireEvent.change(screen.getByLabelText('Số sê-ri'), {
      target: { value: 'SN-003' },
    })
    fireEvent.change(screen.getByLabelText('Mô tả tài sản'), {
      target: { value: 'Tài sản phòng lab mới' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài sản' }))

    await waitFor(() => {
      expect(assetApi.create).toHaveBeenCalledWith({
        assetCode: 'AST-003',
        equipmentTypeId: 1,
        locationId: 10,
        serialNumber: 'SN-003',
        description: 'Tài sản phòng lab mới',
        purchasedAt: null,
        warrantyUntil: null,
      })
    })

    await waitFor(() => {
      expect(
        screen.getByText(/Đã tạo tài sản "AST-003" thành công/),
      ).toBeInTheDocument()
    })
  })

  it('opens Edit modal with prefilled data, sends version and omits assetCode', async () => {
    assetApi.update.mockResolvedValueOnce({
      id: 1,
      assetCode: 'AST-001',
      version: 1,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })

    const editButtons = screen.getAllByRole('button', { name: /Sửa/ })
    fireEvent.click(editButtons[0])

    expect(
      screen.getByRole('heading', { name: /Chỉnh sửa tài sản: AST-001/ }),
    ).toBeInTheDocument()

    const codeInput = screen.getByLabelText(/Mã tài sản/)
    expect(codeInput).toHaveValue('AST-001')
    expect(codeInput).toHaveAttribute('readonly')

    // Change description
    const descInput = screen.getByLabelText('Mô tả tài sản')
    fireEvent.change(descInput, { target: { value: 'Cập nhật mô tả mới' } })

    fireEvent.click(screen.getByRole('button', { name: 'Cập nhật' }))

    await waitFor(() => {
      expect(assetApi.update).toHaveBeenCalledWith(1, {
        version: 0,
        equipmentTypeId: 1,
        locationId: 10,
        serialNumber: 'SN-10001',
        description: 'Cập nhật mô tả mới',
        purchasedAt: '2026-01-15',
        warrantyUntil: '2027-01-15',
      })
    })

    await waitFor(() => {
      expect(
        screen.getByText(/Đã cập nhật tài sản "AST-001" thành công/),
      ).toBeInTheDocument()
    })
  })

  it('opens Status modal, validates reason, and submits changeStatus', async () => {
    assetApi.changeStatus.mockResolvedValueOnce({
      id: 1,
      assetCode: 'AST-001',
      status: 'UNDER_MAINTENANCE',
      version: 1,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })

    const statusButtons = screen.getAllByRole('button', {
      name: /Đổi trạng thái/,
    })
    fireEvent.click(statusButtons[0])

    expect(
      screen.getByRole('heading', { name: 'Chuyển trạng thái tài sản' }),
    ).toBeInTheDocument()

    // Submitting without reason should fail
    fireEvent.click(
      screen.getByRole('button', { name: 'Xác nhận đổi trạng thái' }),
    )
    expect(
      screen.getByText('Lý do chuyển trạng thái là bắt buộc.'),
    ).toBeInTheDocument()

    // Fill valid reason
    fireEvent.change(screen.getByLabelText(/Lý do thay đổi/), {
      target: { value: 'Đưa đi kiểm định định kỳ' },
    })

    fireEvent.click(
      screen.getByRole('button', { name: 'Xác nhận đổi trạng thái' }),
    )

    await waitFor(() => {
      expect(assetApi.changeStatus).toHaveBeenCalledWith(1, {
        targetStatus: 'UNDER_MAINTENANCE',
        reason: 'Đưa đi kiểm định định kỳ',
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

  it('handles 409 conflict during status transition and allows reloading fresh data', async () => {
    const conflictError = new Error('Conflict')
    conflictError.response = {
      status: 409,
      data: {
        title: 'Concurrent update conflict',
        detail: 'The resource was changed by another request.',
      },
    }

    assetApi.changeStatus.mockRejectedValueOnce(conflictError)
    assetApi.getById.mockResolvedValueOnce({
      ...mockAssets[0],
      version: 2,
      status: 'OUT_OF_SERVICE',
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('AST-001')).toBeInTheDocument()
    })

    const statusButtons = screen.getAllByRole('button', {
      name: /Đổi trạng thái/,
    })
    fireEvent.click(statusButtons[0])

    fireEvent.change(screen.getByLabelText(/Lý do thay đổi/), {
      target: { value: 'Bảo trì máy' },
    })

    fireEvent.click(
      screen.getByRole('button', { name: 'Xác nhận đổi trạng thái' }),
    )

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
