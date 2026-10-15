import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { assetApi } from '../api/assetApi'
import { locationApi } from '../../locations/api/locationApi'
import { equipmentTypeApi } from '../../equipmentTypes/api/equipmentTypeApi'
import { CreateAssetPage } from './CreateAssetPage'

vi.mock('../api/assetApi', () => ({
  assetApi: {
    create: vi.fn(),
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
]

const mockLocations = [
  { id: 10, code: 'P301', name: 'Phòng 301', active: true, type: 'ROOM' },
]

describe('CreateAssetPage', () => {
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
    locationApi.getById.mockResolvedValue(mockLocations[0])
  })

  const renderComponent = () =>
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/assets/new']}>
          <Routes>
            <Route path="/assets/new" element={<CreateAssetPage />} />
            <Route
              path="/assets"
              element={<div>Trang danh sách tài sản</div>}
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

  it('renders create form and back link', async () => {
    renderComponent()

    expect(
      screen.getByRole('heading', { name: 'Thêm tài sản mới' }),
    ).toBeInTheDocument()
    expect(screen.getByText('← Danh sách tài sản')).toBeInTheDocument()
    expect(screen.getByLabelText(/Mã tài sản/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Loại thiết bị/)).toBeInTheDocument()
  })

  it('validates empty inputs and creates asset upon valid submit', async () => {
    assetApi.create.mockResolvedValueOnce({
      id: 5,
      assetCode: 'AST-005',
      version: 0,
    })

    renderComponent()

    // Submit empty
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài sản' }))

    expect(
      screen.getByText('Mã tài sản không được để trống.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Vui lòng chọn loại thiết bị.')).toBeInTheDocument()

    // Fill valid
    fireEvent.change(screen.getByLabelText(/Mã tài sản/), {
      target: { value: 'AST-005' },
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

    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài sản' }))

    await waitFor(() => {
      expect(assetApi.create).toHaveBeenCalledWith(
        expect.objectContaining({
          assetCode: 'AST-005',
          equipmentTypeId: 1,
          locationId: 10,
        }),
      )
    })
  })
})
