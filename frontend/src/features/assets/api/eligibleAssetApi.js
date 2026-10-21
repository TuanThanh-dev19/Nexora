import { httpClient } from '../../../shared/api/httpClient'

/**
 * Mock dataset for fallback/offline when backend endpoint is not yet available.
 * Contains assets across multiple locations and all 4 statuses
 * (ACTIVE, UNDER_MAINTENANCE, INACTIVE, RETIRED).
 * Notice: Only ACTIVE and UNDER_MAINTENANCE matching the EXACT locationId are eligible!
 */
export const MOCK_ELIGIBLE_ASSETS = [
  // Location 10 (Room 101)
  {
    id: 101,
    assetCode: 'AST-L10-01',
    description: 'Máy chiếu treo trần Sony VPL-EX455',
    status: 'ACTIVE',
    equipmentType: { id: 1, code: 'PROJ', name: 'Máy chiếu' },
    location: { id: 10, code: 'R101', name: 'Phòng 101' },
  },
  {
    id: 102,
    assetCode: 'AST-L10-02',
    description: 'Điều hòa Daikin Inverter 18000BTU',
    status: 'UNDER_MAINTENANCE',
    equipmentType: { id: 2, code: 'AC', name: 'Điều hòa' },
    location: { id: 10, code: 'R101', name: 'Phòng 101' },
  },
  {
    id: 103,
    assetCode: 'AST-L10-03',
    description: 'Bàn phím cơ văn phòng',
    status: 'INACTIVE', // Inactive - ineligible
    equipmentType: { id: 3, code: 'PERIPH', name: 'Thiết bị ngoại vi' },
    location: { id: 10, code: 'R101', name: 'Phòng 101' },
  },
  {
    id: 104,
    assetCode: 'AST-L10-04',
    description: 'Loa trợ giảng cũ',
    status: 'RETIRED', // Retired - ineligible
    equipmentType: { id: 4, code: 'AUDIO', name: 'Âm thanh' },
    location: { id: 10, code: 'R101', name: 'Phòng 101' },
  },

  // Location 20 (Room 202)
  {
    id: 201,
    assetCode: 'AST-L20-01',
    description: 'Máy tính để bàn HP ProDesk 400',
    status: 'ACTIVE',
    equipmentType: { id: 5, code: 'PC', name: 'Máy tính để bàn' },
    location: { id: 20, code: 'R202', name: 'Phòng 202' },
  },
  {
    id: 202,
    assetCode: 'AST-L20-02',
    description: 'Màn hình Dell UltraSharp 24 inch',
    status: 'UNDER_MAINTENANCE',
    equipmentType: { id: 6, code: 'MONITOR', name: 'Màn hình' },
    location: { id: 20, code: 'R202', name: 'Phòng 202' },
  },
  {
    id: 203,
    assetCode: 'AST-L20-03',
    description: 'Máy in HP LaserJet Pro',
    status: 'INACTIVE', // Inactive - ineligible
    equipmentType: { id: 7, code: 'PRINTER', name: 'Máy in' },
    location: { id: 20, code: 'R202', name: 'Phòng 202' },
  },

  // Ancestor / Parent location 1 (Floor 1)
  {
    id: 901,
    assetCode: 'AST-PARENT-01',
    description: 'Bình cứu hỏa hành lang Tầng 1',
    status: 'ACTIVE',
    equipmentType: { id: 8, code: 'FIRE', name: 'PCCC' },
    location: { id: 1, code: 'F1', name: 'Tầng 1' },
  },
]

/**
 * In-memory Mock Adapter strictly conforming to Issue #38 PageResponse contract:
 * - Only includes assets strictly matching locationId (no parent/child leak)
 * - Only includes status ACTIVE or UNDER_MAINTENANCE
 * - Supports pagination, equipmentTypeId filter, and text query
 */
export function mockEligibleAssetAdapter(
  locationId,
  { page = 0, size = 20, equipmentTypeId, query } = {},
  customDataset = MOCK_ELIGIBLE_ASSETS,
) {
  const numericLocationId = Number(locationId)

  // 1. Filter strictly by exact locationId and eligible statuses
  let filtered = customDataset.filter((item) => {
    const itemLocationId = Number(item.location?.id ?? item.locationId)
    const isExactLocation = itemLocationId === numericLocationId
    const isEligibleStatus =
      item.status === 'ACTIVE' || item.status === 'UNDER_MAINTENANCE'
    return isExactLocation && isEligibleStatus
  })

  // 2. Filter by equipmentTypeId if provided
  if (equipmentTypeId != null && equipmentTypeId !== '') {
    const numericTypeId = Number(equipmentTypeId)
    filtered = filtered.filter(
      (item) => Number(item.equipmentType?.id) === numericTypeId,
    )
  }

  // 3. Filter by query if provided (assetCode, name, or description)
  if (query != null && String(query).trim() !== '') {
    const q = String(query).trim().toLowerCase()
    filtered = filtered.filter((item) => {
      const codeMatch = item.assetCode?.toLowerCase().includes(q)
      const nameMatch = item.name?.toLowerCase().includes(q)
      const descMatch = item.description?.toLowerCase().includes(q)
      const eqMatch = item.equipmentType?.name?.toLowerCase().includes(q)
      return Boolean(codeMatch || nameMatch || descMatch || eqMatch)
    })
  }

  const totalElements = filtered.length
  const totalPages =
    Math.ceil(totalElements / size) || (totalElements === 0 ? 0 : 1)
  const startIndex = page * size
  const content = filtered.slice(startIndex, startIndex + size)

  return {
    content,
    page: Number(page),
    size: Number(size),
    totalElements,
    totalPages,
  }
}

export const eligibleAssetApi = {
  /**
   * Retrieves eligible assets for ticket creation at the specified location.
   * Calls GET /api/v1/locations/{locationId}/eligible-assets
   * Falls back to mock adapter if backend returns 404/501 or network error.
   *
   * @param {number|string} locationId - Required exact location ID
   * @param {Object} [params]
   * @param {number} [params.page=0] - 0-based page index
   * @param {number} [params.size=20] - Page size
   * @param {number} [params.equipmentTypeId] - Optional equipment type filter
   * @param {string} [params.query] - Optional search keyword
   * @param {AbortSignal} [signal]
   * @param {boolean} [params.useFallback=true] - Whether to fallback to mock adapter on failure
   * @returns {Promise<{ content: Array, page: number, size: number, totalElements: number, totalPages: number }>}
   */
  async listEligible(locationId, params = {}, signal) {
    if (locationId == null || locationId === '') {
      return {
        content: [],
        page: 0,
        size: params.size ? Number(params.size) : 20,
        totalElements: 0,
        totalPages: 0,
      }
    }

    const safeParams = {
      page: params.page != null ? Number(params.page) : 0,
      size: params.size != null ? Number(params.size) : 20,
    }

    if (params.equipmentTypeId != null && params.equipmentTypeId !== '') {
      safeParams.equipmentTypeId = Number(params.equipmentTypeId)
    }

    if (params.query != null && String(params.query).trim() !== '') {
      safeParams.query = String(params.query).trim()
    }

    try {
      const response = await httpClient.get(
        `/locations/${encodeURIComponent(locationId)}/eligible-assets`,
        {
          params: safeParams,
          signal,
        },
      )
      return response.data
    } catch (error) {
      // If mock fallback is explicitly disabled, rethrow
      if (params.useFallback === false) {
        throw error
      }

      // If backend returns 404 (endpoint not found / location not found in stub),
      // 501 Not Implemented, or network error in development / test, use contract mock adapter
      const status = error?.response?.status
      const isEndpointMissingOrOffline =
        status === 404 || status === 501 || !error.response

      if (isEndpointMissingOrOffline) {
        return mockEligibleAssetAdapter(locationId, safeParams)
      }

      // Re-throw 400 (Bad Request), 401 (Unauthorized), 403, 500 so UI handles them
      throw error
    }
  },
}
