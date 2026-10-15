import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { locationApi } from '../../locations/api/locationApi'
import { LocationSelector } from '../../locations/components/LocationSelector'
import { equipmentTypeApi } from '../../equipmentTypes/api/equipmentTypeApi'
import {
  eligibleAssetApi,
  mockEligibleAssetAdapter,
} from '../api/eligibleAssetApi'
import { EligibleAssetSelector } from './EligibleAssetSelector'

const mockCampuses = [
  { id: 1, code: 'CAMPUS-1', name: 'Cơ sở 1', type: 'CAMPUS', active: true },
]
const mockBuildings = [
  {
    id: 21,
    code: 'BLD-A',
    name: 'Tòa A',
    type: 'BUILDING',
    parentId: 1,
    active: true,
  },
  {
    id: 22,
    code: 'BLD-B',
    name: 'Tòa B',
    type: 'BUILDING',
    parentId: 1,
    active: true,
  },
]
const mockFloors = [
  {
    id: 51,
    code: 'FL-1',
    name: 'Tầng 1',
    type: 'FLOOR',
    parentId: 21,
    active: true,
  },
]
const mockRooms = [
  {
    id: 10,
    code: 'RM-101',
    name: 'Phòng 101',
    type: 'ROOM',
    parentId: 51,
    active: true,
  },
]

function TicketCreateFormSimulation() {
  const [location, setLocation] = useState(null)
  const [asset, setAsset] = useState(null)

  const handleLocationChange = (nextLocation) => {
    setLocation(nextLocation)
    // Business Rule: changing location clears selected asset
    setAsset(null)
  }

  return (
    <div>
      <LocationSelector
        mode="leaf"
        value={location}
        onChange={handleLocationChange}
      />
      <EligibleAssetSelector
        locationId={location?.id}
        locationName={location?.name}
        value={asset}
        onChange={setAsset}
        policy="REQUIRED"
      />
    </div>
  )
}

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

describe('LocationSelector and EligibleAssetSelector Integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks()

    vi.spyOn(locationApi, 'list').mockImplementation(async (parentId) => {
      if (parentId == null) return mockCampuses
      if (parentId === 1 || parentId === '1') return mockBuildings
      if (parentId === 21 || parentId === '21') return mockFloors
      if (parentId === 51 || parentId === '51') return mockRooms
      return []
    })

    vi.spyOn(equipmentTypeApi, 'list').mockResolvedValue([
      { id: 1, code: 'PROJ', name: 'Máy chiếu', active: true },
    ])

    vi.spyOn(eligibleAssetApi, 'listEligible').mockImplementation(
      async (locId, params) => mockEligibleAssetAdapter(locId, params),
    )
  })

  it('coordinates cascading location selection and eligible asset loading, then auto-clears asset on location change', async () => {
    const user = userEvent.setup()
    renderWithClient(<TicketCreateFormSimulation />)

    // Initially, no location is selected, so EligibleAssetSelector shows missing-location hint
    expect(
      screen.getByText(/Vui lòng chọn địa điểm trước khi chọn thiết bị/i),
    ).toBeInTheDocument()

    // 1. Select Campus 1
    await screen.findByText('Cơ sở 1 (CAMPUS-1)')
    await user.selectOptions(screen.getByLabelText(/Chọn cơ sở/i), '1')

    // 2. Select Building 21
    await screen.findByText('Tòa A (BLD-A)')
    await user.selectOptions(screen.getByLabelText(/Chọn tòa nhà/i), '21')

    // 3. Select Floor 51
    await screen.findByText('Tầng 1 (FL-1)')
    await user.selectOptions(screen.getByLabelText(/Chọn tầng/i), '51')

    // 4. Select Room 10
    await screen.findByText('Phòng 101 (RM-101)')
    await user.selectOptions(
      screen.getByLabelText(/Chọn phòng hoặc khu vực/i),
      '10',
    )

    // EligibleAssetSelector should now load assets for Room 10 (AST-L10-01 and AST-L10-02)
    expect(await screen.findByText('AST-L10-01')).toBeInTheDocument()
    expect(screen.getByText('AST-L10-02')).toBeInTheDocument()

    // 5. Select Asset AST-L10-01
    const selectAssetBtn = screen.getByRole('button', {
      name: /Chọn thiết bị AST-L10-01/i,
    })
    await user.click(selectAssetBtn)

    // Selected Asset Summary Card should be shown
    expect(await screen.findByText('Active')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /Đổi thiết bị/i }),
    ).toBeInTheDocument()

    // 6. Change Building in LocationSelector -> Cascade Reset clears Room 10
    await user.selectOptions(screen.getByLabelText(/Chọn tòa nhà/i), '22')

    // EligibleAssetSelector must have auto-cleared the selected asset
    // and reverted back to the missing-location hint
    await waitFor(() => {
      expect(
        screen.getByText(/Vui lòng chọn địa điểm trước khi chọn thiết bị/i),
      ).toBeInTheDocument()
    })
    expect(screen.queryByText('Active')).not.toBeInTheDocument()
  })
})
