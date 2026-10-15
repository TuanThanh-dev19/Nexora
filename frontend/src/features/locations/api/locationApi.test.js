import { beforeEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { locationApi } from './locationApi'
import { formatLocationError } from './locationError'

vi.mock('../../../shared/api/httpClient', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}))

describe('locationApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('list: calls GET /locations without parentId for roots', async () => {
    const mockRoots = [{ id: 1, code: 'CS1', name: 'Cơ sở 1', type: 'CAMPUS', parentId: null, active: true }]
    httpClient.get.mockResolvedValueOnce({ data: mockRoots })

    const result = await locationApi.list(null, true)

    expect(httpClient.get).toHaveBeenCalledWith('/locations', {
      params: { active: true },
      signal: undefined,
    })
    expect(result).toEqual(mockRoots)
  })

  it('list: calls GET /locations with parentId for direct children', async () => {
    const mockBuildings = [{ id: 2, code: 'TOA-A', name: 'Tòa A', type: 'BUILDING', parentId: 1, active: true }]
    httpClient.get.mockResolvedValueOnce({ data: mockBuildings })

    const result = await locationApi.list(1, false)

    expect(httpClient.get).toHaveBeenCalledWith('/locations', {
      params: { parentId: 1, active: false },
      signal: undefined,
    })
    expect(result).toEqual(mockBuildings)
  })

  it('getById: calls GET /locations/{locationId}', async () => {
    const mockLocation = { id: 5, code: 'P302', name: 'Phòng 302', type: 'ROOM', parentId: 4, active: true }
    httpClient.get.mockResolvedValueOnce({ data: mockLocation })

    const result = await locationApi.getById(5)

    expect(httpClient.get).toHaveBeenCalledWith('/locations/5', { signal: undefined })
    expect(result).toEqual(mockLocation)
  })

  it('create: calls POST /admin/locations with sanitized payload', async () => {
    const createdLocation = { id: 10, code: 'LAB-1', name: 'Phòng Lab 1', type: 'ROOM', parentId: 3, description: 'Lab thực hành' }
    httpClient.post.mockResolvedValueOnce({ data: createdLocation })

    const payload = {
      code: '  LAB-1  ',
      name: '  Phòng Lab 1  ',
      type: 'ROOM',
      parentId: '3',
      description: '  Lab thực hành  ',
    }

    const result = await locationApi.create(payload)

    expect(httpClient.post).toHaveBeenCalledWith('/admin/locations', {
      code: 'LAB-1',
      name: 'Phòng Lab 1',
      type: 'ROOM',
      parentId: 3,
      description: 'Lab thực hành',
    })
    expect(result).toEqual(createdLocation)
  })

  it('update: calls PUT /admin/locations/{id} and excludes code and version', async () => {
    const updatedLocation = { id: 2, code: 'TOA-A', name: 'Tòa A mới', parentId: 1, description: 'Đã sửa', active: false }
    httpClient.put.mockResolvedValueOnce({ data: updatedLocation })

    const payload = {
      code: 'ILLEGAL_CODE_CHANGE',
      version: 99,
      name: '  Tòa A mới  ',
      parentId: '1',
      description: '  Đã sửa  ',
      active: false,
    }

    const result = await locationApi.update(2, payload)

    expect(httpClient.put).toHaveBeenCalledWith('/admin/locations/2', {
      name: 'Tòa A mới',
      parentId: 1,
      description: 'Đã sửa',
      active: false,
    })
    expect(result).toEqual(updatedLocation)
  })
})

describe('formatLocationError', () => {
  it('formats duplicate code error', () => {
    const err = { response: { status: 422, data: { detail: "Location code already exists: 'CS1'" } } }
    expect(formatLocationError(err)).toBe('Mã địa điểm này đã tồn tại trong hệ thống.')
  })

  it('formats cycle error', () => {
    const err = { response: { status: 422, data: { detail: 'Updating the parent would create a cycle in the location hierarchy' } } }
    expect(formatLocationError(err)).toBe('Không thể chọn địa điểm cha vì sẽ tạo vòng lặp trong phân cấp.')
  })

  it('formats root location error', () => {
    const err = { response: { status: 422, data: { detail: 'CAMPUS must be a root location (no parent allowed)' } } }
    expect(formatLocationError(err)).toBe('Cơ sở (CAMPUS) phải là địa điểm gốc và không được có địa điểm cha.')
  })

  it('formats standard HTTP status codes', () => {
    expect(formatLocationError({ response: { status: 403 } })).toContain('không có quyền')
    expect(formatLocationError({ response: { status: 404 } })).toContain('Không tìm thấy địa điểm')
    expect(formatLocationError({ response: { status: 401 } })).toContain('Phiên làm việc đã hết hạn')
    expect(formatLocationError(new Error('Network error'))).toContain('Không thể kết nối đến máy chủ')
  })
})
