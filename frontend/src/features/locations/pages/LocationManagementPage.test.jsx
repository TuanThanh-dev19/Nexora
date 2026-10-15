import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { locationApi } from '../api/locationApi'
import { LocationManagementPage } from './LocationManagementPage'

vi.mock('../api/locationApi', () => ({
  locationApi: {
    list: vi.fn(),
    getById: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}))

const mockRoots = [
  {
    id: 1,
    code: 'CS1',
    name: 'Cơ sở 1',
    type: 'CAMPUS',
    parentId: null,
    description: 'Khuôn viên chính',
    active: true,
  },
]

const mockBuildings = [
  {
    id: 2,
    code: 'TOA-A',
    name: 'Tòa Nhà Alpha',
    type: 'BUILDING',
    parentId: 1,
    description: 'Khu phòng học',
    active: true,
  },
]

describe('LocationManagementPage', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })

    // Default mock behavior
    locationApi.list.mockImplementation(async (parentId) => {
      if (parentId == null) return mockRoots
      if (parentId === 1) return mockBuildings
      return []
    })

    locationApi.getById.mockImplementation(async (id) => {
      if (id === 1) return mockRoots[0]
      if (id === 2) return mockBuildings[0]
      return null
    })
  })

  const renderComponent = () =>
    render(
      <QueryClientProvider client={queryClient}>
        <LocationManagementPage />
      </QueryClientProvider>,
    )

  it('renders page heading, tree panel, and empty detail placeholder', async () => {
    renderComponent()

    expect(screen.getByRole('heading', { name: 'Quản lý địa điểm' })).toBeInTheDocument()
    expect(screen.getByText('Cây địa điểm')).toBeInTheDocument()
    expect(screen.getByText('Chưa chọn địa điểm')).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('Cơ sở 1')).toBeInTheDocument()
      expect(screen.getByText('(CS1)')).toBeInTheDocument()
    })
  })

  it('selects a node from tree and displays details in right panel', async () => {
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Cơ sở 1')).toBeInTheDocument()
    })

    // Click on the tree item row for Cơ sở 1
    const nodeItem = screen.getByRole('button', { name: /Cơ sở 1 \(CS1\)/i })
    fireEvent.click(nodeItem)

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Cơ sở 1' })).toBeInTheDocument()
      expect(screen.getByText('Khuôn viên chính')).toBeInTheDocument()
      expect(screen.getByText('Địa điểm gốc (Cơ sở — không có cha)')).toBeInTheDocument()
    })
  })

  it('expands tree node and lazy loads child locations', async () => {
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Cơ sở 1')).toBeInTheDocument()
    })

    // Toggle expand button for Cơ sở 1
    const expandBtn = screen.getByLabelText('Mở rộng Cơ sở 1')
    fireEvent.click(expandBtn)

    await waitFor(() => {
      expect(locationApi.list).toHaveBeenCalledWith(1, true, expect.anything())
      expect(screen.getByText('Tòa Nhà Alpha')).toBeInTheDocument()
      expect(screen.getByText('(TOA-A)')).toBeInTheDocument()
    })
  })

  it('opens create modal, fills fields and creates a location', async () => {
    locationApi.create.mockResolvedValueOnce({
      id: 3,
      code: 'CS2',
      name: 'Cơ sở Hòa Lạc',
      type: 'CAMPUS',
      parentId: null,
      description: 'Cơ sở vệ tinh',
      active: true,
    })

    renderComponent()

    const addBtn = screen.getByRole('button', { name: /Thêm địa điểm/i })
    fireEvent.click(addBtn)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Thêm địa điểm mới')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Mã địa điểm/i), { target: { value: 'CS2' } })
    fireEvent.change(screen.getByLabelText(/Tên địa điểm/i), { target: { value: 'Cơ sở Hòa Lạc' } })
    fireEvent.change(screen.getByLabelText(/Mô tả/i), { target: { value: 'Cơ sở vệ tinh' } })

    fireEvent.click(screen.getByRole('button', { name: 'Tạo địa điểm' }))

    await waitFor(() => {
      expect(locationApi.create).toHaveBeenCalledWith({
        code: 'CS2',
        name: 'Cơ sở Hòa Lạc',
        type: 'CAMPUS',
        parentId: null,
        description: 'Cơ sở vệ tinh',
      })
    })
  })

  it('opens edit modal and updates location without sending code or version', async () => {
    locationApi.update.mockResolvedValueOnce({
      id: 1,
      code: 'CS1',
      name: 'Cơ sở 1 Mới',
      type: 'CAMPUS',
      parentId: null,
      description: 'Mô tả mới',
      active: true,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Cơ sở 1')).toBeInTheDocument()
    })

    // Select the location
    fireEvent.click(screen.getByRole('button', { name: /Cơ sở 1 \(CS1\)/i }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Chỉnh sửa/i })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /Chỉnh sửa/i }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Chỉnh sửa địa điểm: Cơ sở 1')).toBeInTheDocument()

    const codeInput = screen.getByLabelText(/Mã địa điểm/i)
    expect(codeInput).toHaveAttribute('readonly')

    const nameInput = screen.getByLabelText(/Tên địa điểm/i)
    fireEvent.change(nameInput, { target: { value: 'Cơ sở 1 Mới' } })

    fireEvent.click(screen.getByRole('button', { name: 'Cập nhật' }))

    await waitFor(() => {
      expect(locationApi.update).toHaveBeenCalledWith(1, {
        name: 'Cơ sở 1 Mới',
        parentId: null,
        description: 'Khuôn viên chính',
        active: true,
      })
    })
  })

  it('deactivates location via confirmation dialog using PUT with active: false', async () => {
    locationApi.update.mockResolvedValueOnce({
      id: 1,
      code: 'CS1',
      name: 'Cơ sở 1',
      type: 'CAMPUS',
      parentId: null,
      description: 'Khuôn viên chính',
      active: false,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Cơ sở 1')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /Cơ sở 1 \(CS1\)/i }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Ngừng hoạt động/i })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /Ngừng hoạt động/i }))

    expect(screen.getByRole('heading', { name: /Xác nhận ngừng hoạt động/i })).toBeInTheDocument()
    expect(screen.getByText(/Bạn có chắc chắn muốn ngừng hoạt động địa điểm/i)).toBeInTheDocument()

    const confirmBtn = screen.getByRole('button', { name: 'Xác nhận ngừng hoạt động' })
    fireEvent.click(confirmBtn)

    await waitFor(() => {
      expect(locationApi.update).toHaveBeenCalledWith(1, {
        name: 'Cơ sở 1',
        parentId: null,
        description: 'Khuôn viên chính',
        active: false,
      })
    })
  })
})
