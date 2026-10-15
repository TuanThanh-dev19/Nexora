import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { locationApi } from '../api/locationApi'
import { LocationSelector } from './LocationSelector'

const mockCampuses = [
  {
    id: 1,
    code: 'CAMPUS-A',
    name: 'Cơ sở Quận 1',
    type: 'CAMPUS',
    active: true,
  },
  {
    id: 2,
    code: 'CAMPUS-B',
    name: 'Cơ sở Thủ Đức',
    type: 'CAMPUS',
    active: true,
  },
]

const mockBuildings = [
  {
    id: 10,
    code: 'BLD-A1',
    name: 'Tòa Nhà A1',
    type: 'BUILDING',
    parentId: 1,
    active: true,
  },
  {
    id: 11,
    code: 'BLD-A2',
    name: 'Tòa Nhà A2',
    type: 'BUILDING',
    parentId: 1,
    active: true,
  },
]

const mockFloors = [
  {
    id: 100,
    code: 'FL-01',
    name: 'Tầng 1',
    type: 'FLOOR',
    parentId: 10,
    active: true,
  },
  {
    id: 101,
    code: 'FL-02',
    name: 'Tầng 2',
    type: 'FLOOR',
    parentId: 10,
    active: true,
  },
]

const mockRooms = [
  {
    id: 1001,
    code: 'RM-101',
    name: 'Phòng Lab 101',
    type: 'ROOM',
    parentId: 100,
    active: true,
  },
  {
    id: 1002,
    code: 'RM-102',
    name: 'Phòng Hội Thảo 102',
    type: 'ROOM',
    parentId: 100,
    active: true,
  },
]

function renderWithClient(ui) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        staleTime: Infinity,
      },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  )
}

describe('LocationSelector', () => {
  beforeEach(() => {
    vi.restoreAllMocks()

    vi.spyOn(locationApi, 'list').mockImplementation(async (parentId) => {
      if (parentId == null) return mockCampuses
      if (parentId === 1 || parentId === '1') return mockBuildings
      if (parentId === 10 || parentId === '10') return mockFloors
      if (parentId === 100 || parentId === '100') return mockRooms
      return []
    })
  })

  it('renders all four cascading dropdowns with proper labels and initial disabled states', async () => {
    renderWithClient(<LocationSelector mode="leaf" />)

    // Campus is enabled
    const campusSelect = await screen.findByLabelText(/Chọn cơ sở/i)
    await waitFor(() => expect(campusSelect).toBeEnabled())
    expect(
      await screen.findByText('Cơ sở Quận 1 (CAMPUS-A)'),
    ).toBeInTheDocument()

    // Building, Floor, Room are disabled until parent is selected
    expect(screen.getByLabelText(/Chọn tòa nhà/i)).toBeDisabled()
    expect(screen.getByLabelText(/Chọn tầng/i)).toBeDisabled()
    expect(screen.getByLabelText(/Chọn phòng hoặc khu vực/i)).toBeDisabled()
  })

  it('cascades selections down to leaf room and calls onChange with selected leaf', async () => {
    const user = userEvent.setup()
    const handleChange = vi.fn()

    renderWithClient(<LocationSelector mode="leaf" onChange={handleChange} />)

    // 1. Wait for campus options and select Campus 1
    await screen.findByText('Cơ sở Quận 1 (CAMPUS-A)')
    await user.selectOptions(screen.getByLabelText(/Chọn cơ sở/i), '1')

    // 2. Wait for building options and select Building 10
    await screen.findByText('Tòa Nhà A1 (BLD-A1)')
    await user.selectOptions(screen.getByLabelText(/Chọn tòa nhà/i), '10')

    // 3. Wait for floor options and select Floor 100
    await screen.findByText('Tầng 1 (FL-01)')
    await user.selectOptions(screen.getByLabelText(/Chọn tầng/i), '100')

    // 4. Wait for room options and select Room 1001
    await screen.findByText('Phòng Lab 101 (RM-101)')
    await user.selectOptions(
      screen.getByLabelText(/Chọn phòng hoặc khu vực/i),
      '1001',
    )

    // Should call onChange with the room
    expect(handleChange).toHaveBeenLastCalledWith(mockRooms[0])

    // Breadcrumb should display the full path
    expect(screen.getByText('Phòng Lab 101')).toBeInTheDocument()
  })

  it('resets all child levels and calls onChange(null) when parent level changes (Cascade Reset)', async () => {
    const user = userEvent.setup()
    const handleChange = vi.fn()

    renderWithClient(<LocationSelector mode="leaf" onChange={handleChange} />)

    // Select down to room
    await screen.findByText('Cơ sở Quận 1 (CAMPUS-A)')
    await user.selectOptions(screen.getByLabelText(/Chọn cơ sở/i), '1')

    await screen.findByText('Tòa Nhà A1 (BLD-A1)')
    await user.selectOptions(screen.getByLabelText(/Chọn tòa nhà/i), '10')

    await screen.findByText('Tầng 1 (FL-01)')
    await user.selectOptions(screen.getByLabelText(/Chọn tầng/i), '100')

    await screen.findByText('Phòng Lab 101 (RM-101)')
    await user.selectOptions(
      screen.getByLabelText(/Chọn phòng hoặc khu vực/i),
      '1001',
    )

    expect(handleChange).toHaveBeenLastCalledWith(mockRooms[0])

    // Now change Building back to empty or another building
    await user.selectOptions(screen.getByLabelText(/Chọn tòa nhà/i), '')

    // Floor and Room should be cleared and disabled
    expect(screen.getByLabelText(/Chọn tầng/i)).toBeDisabled()
    expect(screen.getByLabelText(/Chọn phòng hoặc khu vực/i)).toBeDisabled()

    // onChange should have been called with null to reset downstream (including eligible asset!)
    expect(handleChange).toHaveBeenLastCalledWith(null)
  })

  it('supports node mode allowing selection of any hierarchy node (e.g. Building)', async () => {
    const user = userEvent.setup()
    const handleChange = vi.fn()

    renderWithClient(<LocationSelector mode="node" onChange={handleChange} />)

    // Select Campus 1 -> in node mode, it immediately selects Campus 1
    await screen.findByText('Cơ sở Quận 1 (CAMPUS-A)')
    await user.selectOptions(screen.getByLabelText(/Chọn cơ sở/i), '1')
    expect(handleChange).toHaveBeenLastCalledWith(mockCampuses[0])

    // Select Building 10 -> in node mode, it updates selection to Building 10
    await screen.findByText('Tòa Nhà A1 (BLD-A1)')
    await user.selectOptions(screen.getByLabelText(/Chọn tòa nhà/i), '10')
    expect(handleChange).toHaveBeenLastCalledWith(mockBuildings[0])
  })

  it('clears all levels when clicking Clear button', async () => {
    const user = userEvent.setup()
    const handleChange = vi.fn()
    const handleReset = vi.fn()

    renderWithClient(
      <LocationSelector
        mode="leaf"
        onChange={handleChange}
        onReset={handleReset}
      />,
    )

    await screen.findByText('Cơ sở Quận 1 (CAMPUS-A)')
    await user.selectOptions(screen.getByLabelText(/Chọn cơ sở/i), '1')

    const clearBtn = await screen.findByRole('button', {
      name: /Xóa lựa chọn/i,
    })
    await user.click(clearBtn)

    expect(handleChange).toHaveBeenLastCalledWith(null)
    expect(handleReset).toHaveBeenCalled()
    expect(screen.getByLabelText(/Chọn cơ sở/i)).toHaveValue('')
  })

  it('displays field validation error message when error prop is provided', () => {
    renderWithClient(
      <LocationSelector
        error="Vui lòng chọn địa điểm phòng học."
        isInvalid={true}
      />,
    )

    expect(
      screen.getByText('Vui lòng chọn địa điểm phòng học.'),
    ).toBeInTheDocument()
  })

  it('handles level query error and provides Retry button', async () => {
    const user = userEvent.setup()
    vi.spyOn(locationApi, 'list').mockRejectedValue({
      response: { status: 400 },
    })

    renderWithClient(<LocationSelector />)

    expect(await screen.findByText('Lỗi tải cơ sở')).toBeInTheDocument()
    const retryBtn = screen.getByRole('button', { name: /Thử lại/i })
    expect(retryBtn).toBeInTheDocument()

    // Mock success on retry
    vi.spyOn(locationApi, 'list').mockResolvedValueOnce(mockCampuses)
    await user.click(retryBtn)

    expect(
      await screen.findByText('Cơ sở Quận 1 (CAMPUS-A)'),
    ).toBeInTheDocument()
  })
})
