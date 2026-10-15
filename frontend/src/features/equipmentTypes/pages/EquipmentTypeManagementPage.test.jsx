import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { equipmentTypeApi } from '../api/equipmentTypeApi'
import { EquipmentTypeManagementPage } from './EquipmentTypeManagementPage'

vi.mock('../api/equipmentTypeApi', () => ({
  equipmentTypeApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}))

const mockEquipmentTypes = [
  {
    id: 1,
    code: 'PROJECTOR',
    name: 'Máy chiếu',
    description: 'Máy chiếu lớp học',
    active: true,
  },
  {
    id: 2,
    code: 'AIR_CON',
    name: 'Điều hòa',
    description: 'Điều hòa nhiệt độ',
    active: false,
  },
]

describe('EquipmentTypeManagementPage', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })
    equipmentTypeApi.list.mockResolvedValue(mockEquipmentTypes)
  })

  const renderComponent = () =>
    render(
      <QueryClientProvider client={queryClient}>
        <EquipmentTypeManagementPage />
      </QueryClientProvider>,
    )

  it('renders heading, active filter and equipment types table', async () => {
    renderComponent()

    expect(screen.getByRole('heading', { name: 'Loại thiết bị' })).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('Máy chiếu')).toBeInTheDocument()
      expect(screen.getByText('PROJECTOR')).toBeInTheDocument()
      expect(screen.getByText('Điều hòa')).toBeInTheDocument()
      expect(screen.getByText('AIR_CON')).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: /Thêm loại thiết bị/i })).toBeInTheDocument()
  })

  it('toggles active filter switch and calls list with updated flag', async () => {
    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Máy chiếu')).toBeInTheDocument()
    })

    const filterSwitch = screen.getByLabelText(/Chỉ hiển thị loại thiết bị đang hoạt động/i)
    fireEvent.click(filterSwitch)

    await waitFor(() => {
      expect(equipmentTypeApi.list).toHaveBeenCalledWith(false, expect.anything())
    })
  })

  it('opens create modal and submits new equipment type', async () => {
    equipmentTypeApi.create.mockResolvedValueOnce({
      id: 3,
      code: 'SPEAKER',
      name: 'Loa hội trường',
      description: 'Âm thanh',
      active: true,
    })

    renderComponent()

    const addBtn = screen.getByRole('button', { name: /Thêm loại thiết bị/i })
    fireEvent.click(addBtn)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Thêm loại thiết bị mới')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Mã loại thiết bị/i), { target: { value: 'SPEAKER' } })
    fireEvent.change(screen.getByLabelText(/Tên loại thiết bị/i), { target: { value: 'Loa hội trường' } })
    fireEvent.change(screen.getByLabelText(/Mô tả/i), { target: { value: 'Âm thanh' } })

    fireEvent.click(screen.getByRole('button', { name: 'Tạo loại thiết bị' }))

    await waitFor(() => {
      expect(equipmentTypeApi.create).toHaveBeenCalledWith({
        code: 'SPEAKER',
        name: 'Loa hội trường',
        description: 'Âm thanh',
      })
    })
  })

  it('opens edit modal with read-only code and submits update', async () => {
    equipmentTypeApi.update.mockResolvedValueOnce({
      id: 1,
      code: 'PROJECTOR',
      name: 'Máy chiếu siêu nét',
      description: 'Đã nâng cấp',
      active: true,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Máy chiếu')).toBeInTheDocument()
    })

    const editBtn = screen.getByRole('button', { name: 'Sửa Máy chiếu' })
    fireEvent.click(editBtn)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Sửa loại thiết bị: Máy chiếu')).toBeInTheDocument()

    const codeInput = screen.getByLabelText(/Mã loại thiết bị/i)
    expect(codeInput).toHaveAttribute('readonly')

    const nameInput = screen.getByLabelText(/Tên loại thiết bị/i)
    fireEvent.change(nameInput, { target: { value: 'Máy chiếu siêu nét' } })

    fireEvent.click(screen.getByRole('button', { name: 'Cập nhật' }))

    await waitFor(() => {
      expect(equipmentTypeApi.update).toHaveBeenCalledWith(1, {
        name: 'Máy chiếu siêu nét',
        description: 'Máy chiếu lớp học',
        active: true,
      })
    })
  })

  it('opens deactivation modal and calls update with active: false', async () => {
    equipmentTypeApi.update.mockResolvedValueOnce({
      id: 1,
      code: 'PROJECTOR',
      name: 'Máy chiếu',
      description: 'Máy chiếu lớp học',
      active: false,
    })

    renderComponent()

    await waitFor(() => {
      expect(screen.getByText('Máy chiếu')).toBeInTheDocument()
    })

    const deactivateBtn = screen.getByRole('button', { name: 'Ngừng hoạt động Máy chiếu' })
    fireEvent.click(deactivateBtn)

    expect(screen.getByRole('heading', { name: /Xác nhận ngừng hoạt động/i })).toBeInTheDocument()
    expect(screen.getByText(/Bạn có chắc chắn muốn ngừng hoạt động/i)).toBeInTheDocument()

    const confirmBtn = screen.getByRole('button', { name: 'Xác nhận ngừng hoạt động' })
    fireEvent.click(confirmBtn)

    await waitFor(() => {
      expect(equipmentTypeApi.update).toHaveBeenCalledWith(1, {
        name: 'Máy chiếu',
        description: 'Máy chiếu lớp học',
        active: false,
      })
    })
  })
})
