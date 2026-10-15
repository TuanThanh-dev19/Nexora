import { beforeEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { equipmentTypeApi } from './equipmentTypeApi'
import { formatEquipmentTypeError } from './equipmentTypeError'

vi.mock('../../../shared/api/httpClient', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}))

describe('equipmentTypeApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('list: calls GET /equipment-types with active flag', async () => {
    const mockData = [
      { id: 1, code: 'PROJ', name: 'Máy chiếu', description: 'Máy chiếu', active: true },
    ]
    httpClient.get.mockResolvedValueOnce({ data: mockData })

    const result = await equipmentTypeApi.list(true)

    expect(httpClient.get).toHaveBeenCalledWith('/equipment-types', {
      params: { active: true },
      signal: undefined,
    })
    expect(result).toEqual(mockData)
  })

  it('create: calls POST /admin/equipment-types with trimmed payload', async () => {
    const createdType = { id: 2, code: 'AC', name: 'Điều hòa', description: 'Máy lạnh' }
    httpClient.post.mockResolvedValueOnce({ data: createdType })

    const payload = {
      code: '  AC  ',
      name: '  Điều hòa  ',
      description: '  Máy lạnh  ',
    }

    const result = await equipmentTypeApi.create(payload)

    expect(httpClient.post).toHaveBeenCalledWith('/admin/equipment-types', {
      code: 'AC',
      name: 'Điều hòa',
      description: 'Máy lạnh',
    })
    expect(result).toEqual(createdType)
  })

  it('update: calls PUT /admin/equipment-types/{id} and strictly excludes code and version', async () => {
    const updatedType = { id: 1, code: 'PROJ', name: 'Máy chiếu cao cấp', description: 'Mới', active: false }
    httpClient.put.mockResolvedValueOnce({ data: updatedType })

    const payload = {
      code: 'CANNOT_MODIFY',
      version: 5,
      name: '  Máy chiếu cao cấp  ',
      description: '  Mới  ',
      active: false,
    }

    const result = await equipmentTypeApi.update(1, payload)

    expect(httpClient.put).toHaveBeenCalledWith('/admin/equipment-types/1', {
      name: 'Máy chiếu cao cấp',
      description: 'Mới',
      active: false,
    })
    expect(result).toEqual(updatedType)
  })
})

describe('formatEquipmentTypeError', () => {
  it('formats duplicate code error', () => {
    const err = { response: { status: 422, data: { detail: "EquipmentType code already exists: 'PROJ'" } } }
    expect(formatEquipmentTypeError(err)).toBe('Mã loại thiết bị này đã tồn tại trong hệ thống.')
  })

  it('formats standard HTTP status codes', () => {
    expect(formatEquipmentTypeError({ response: { status: 403 } })).toContain('không có quyền')
    expect(formatEquipmentTypeError({ response: { status: 404 } })).toContain('Không tìm thấy loại thiết bị')
    expect(formatEquipmentTypeError({ response: { status: 401 } })).toContain('Phiên làm việc đã hết hạn')
    expect(formatEquipmentTypeError(new Error('Network error'))).toContain('Không thể kết nối đến máy chủ')
  })
})
