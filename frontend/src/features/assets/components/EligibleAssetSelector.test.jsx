import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { equipmentTypeApi } from '../../equipmentTypes/api/equipmentTypeApi'
import {
  eligibleAssetApi,
  mockEligibleAssetAdapter,
} from '../api/eligibleAssetApi'
import { EligibleAssetSelector } from './EligibleAssetSelector'

const mockEquipmentTypes = [
  { id: 1, code: 'PROJ', name: 'Máy chiếu', active: true },
  { id: 2, code: 'AC', name: 'Điều hòa', active: true },
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

describe('EligibleAssetSelector', () => {
  beforeEach(() => {
    vi.restoreAllMocks()

    vi.spyOn(equipmentTypeApi, 'list').mockResolvedValue(mockEquipmentTypes)
    vi.spyOn(eligibleAssetApi, 'listEligible').mockImplementation(
      async (locId, params) => mockEligibleAssetAdapter(locId, params),
    )
  })

  it('renders missing-location hint when locationId is not provided', () => {
    renderWithClient(<EligibleAssetSelector locationId={null} />)

    expect(
      screen.getByText(/Vui lòng chọn địa điểm trước khi chọn thiết bị/i),
    ).toBeInTheDocument()
  })

  it('renders FORBIDDEN policy note and clears selected asset if provided', () => {
    const handleChange = vi.fn()
    const sampleAsset = {
      id: 101,
      assetCode: 'AST-L10-01',
      status: 'ACTIVE',
      location: { id: 10 },
    }

    renderWithClient(
      <EligibleAssetSelector
        locationId={10}
        value={sampleAsset}
        policy="FORBIDDEN"
        onChange={handleChange}
      />,
    )

    expect(screen.getByText(/không áp dụng chọn thiết bị/i)).toBeInTheDocument()
    expect(handleChange).toHaveBeenCalledWith(null)
  })

  it('displays only ACTIVE and UNDER_MAINTENANCE assets for the exact locationId', async () => {
    renderWithClient(<EligibleAssetSelector locationId={10} />)

    // Location 10 in mock dataset has:
    // - AST-L10-01 (ACTIVE) -> should be visible
    // - AST-L10-02 (UNDER_MAINTENANCE) -> should be visible
    // - AST-L10-03 (INACTIVE) -> MUST NOT be visible
    // - AST-L10-04 (RETIRED) -> MUST NOT be visible
    // - AST-PARENT-01 (Location 1) -> MUST NOT be visible
    expect(await screen.findByText('AST-L10-01')).toBeInTheDocument()
    expect(screen.getByText('AST-L10-02')).toBeInTheDocument()

    expect(screen.queryByText('AST-L10-03')).not.toBeInTheDocument()
    expect(screen.queryByText('AST-L10-04')).not.toBeInTheDocument()
    expect(screen.queryByText('AST-PARENT-01')).not.toBeInTheDocument()
  })

  it('automatically clears selected asset when locationId prop changes', () => {
    const handleChange = vi.fn()
    const sampleAsset = {
      id: 101,
      assetCode: 'AST-L10-01',
      status: 'ACTIVE',
      location: { id: 10 },
    }

    const { rerender } = renderWithClient(
      <EligibleAssetSelector
        locationId={10}
        value={sampleAsset}
        onChange={handleChange}
      />,
    )

    // Now change location to 20
    rerender(
      <QueryClientProvider client={new QueryClient()}>
        <EligibleAssetSelector
          locationId={20}
          value={sampleAsset}
          onChange={handleChange}
        />
      </QueryClientProvider>,
    )

    expect(handleChange).toHaveBeenCalledWith(null)
  })

  it('renders Selected Asset Summary card when an asset is selected and allows changing it', async () => {
    const user = userEvent.setup()
    const selectedAsset = {
      id: 101,
      assetCode: 'AST-L10-01',
      description: 'Máy chiếu Sony',
      status: 'ACTIVE',
      equipmentType: { id: 1, name: 'Máy chiếu' },
      location: { id: 10, code: 'R101', name: 'Phòng 101' },
    }

    renderWithClient(
      <EligibleAssetSelector
        locationId={10}
        value={selectedAsset}
        policy="OPTIONAL"
      />,
    )

    expect(screen.getByText('AST-L10-01')).toBeInTheDocument()
    expect(screen.getByText('Máy chiếu Sony')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()

    // Click "Đổi thiết bị"
    const changeBtn = screen.getByRole('button', { name: /Đổi thiết bị/i })
    await user.click(changeBtn)

    // Asset selection list should open
    expect(await screen.findByText('AST-L10-02')).toBeInTheDocument()
  })

  it('detects Stale Reference when selected asset does not match current location and displays warning', () => {
    const staleAsset = {
      id: 101,
      assetCode: 'AST-L10-01',
      status: 'ACTIVE',
      location: { id: 10, code: 'R101', name: 'Phòng 101' },
    }
    const handleStale = vi.fn()

    renderWithClient(
      <EligibleAssetSelector
        locationId={999} // Different location!
        locationName="Phòng 999"
        value={staleAsset}
        onStale={handleStale}
      />,
    )

    expect(screen.getByText(/Thiết bị không còn hợp lệ/i)).toBeInTheDocument()
    expect(screen.getByText(/không còn thuộc địa điểm/i)).toBeInTheDocument()
    expect(handleStale).toHaveBeenCalledWith(staleAsset)
  })

  it('detects Stale Reference when selected asset status is INACTIVE or RETIRED', () => {
    const retiredAsset = {
      id: 101,
      assetCode: 'AST-L10-01',
      status: 'RETIRED',
      location: { id: 10, code: 'R101', name: 'Phòng 101' },
    }

    renderWithClient(
      <EligibleAssetSelector locationId={10} value={retiredAsset} />,
    )

    expect(screen.getByText(/Thiết bị không còn hợp lệ/i)).toBeInTheDocument()
  })

  it('filters by equipment type and searches by keyword', async () => {
    const user = userEvent.setup()

    renderWithClient(<EligibleAssetSelector locationId={10} />)

    expect(await screen.findByText('AST-L10-01')).toBeInTheDocument()
    expect(screen.getByText('AST-L10-02')).toBeInTheDocument()

    // Filter by equipment type 1 (PROJ - Máy chiếu)
    const typeSelect = screen.getByLabelText(/Lọc theo loại thiết bị/i)
    await user.selectOptions(typeSelect, '1')

    await waitFor(() => {
      expect(screen.getByText('AST-L10-01')).toBeInTheDocument()
      expect(screen.queryByText('AST-L10-02')).not.toBeInTheDocument()
    })

    // Search query with no match
    const searchInput = screen.getByLabelText(/Tìm kiếm thiết bị/i)
    await user.clear(searchInput)
    await user.type(searchInput, 'KhongCoGi')

    expect(
      await screen.findByText(/Không tìm thấy thiết bị phù hợp với bộ lọc/i),
    ).toBeInTheDocument()
  })

  it('handles API error with Retry button', async () => {
    const user = userEvent.setup()
    vi.spyOn(eligibleAssetApi, 'listEligible').mockRejectedValueOnce({
      response: { status: 400 },
    })

    renderWithClient(<EligibleAssetSelector locationId={10} />)

    expect(
      await screen.findByText(/Không tải được danh sách thiết bị/i),
    ).toBeInTheDocument()

    const retryBtn = screen.getByRole('button', { name: /Thử lại/i })
    expect(retryBtn).toBeInTheDocument()

    // On retry, succeed
    vi.spyOn(eligibleAssetApi, 'listEligible').mockImplementation(
      async (locId, params) => mockEligibleAssetAdapter(locId, params),
    )
    await user.click(retryBtn)

    expect(await screen.findByText('AST-L10-01')).toBeInTheDocument()
  })
})
