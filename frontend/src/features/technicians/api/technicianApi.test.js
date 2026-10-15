import { beforeEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { locationApi } from '../../locations/api/locationApi'
import { technicianApi } from './technicianApi'
import { formatTechnicianError, isConflictError } from './technicianError'

vi.mock('../../../shared/api/httpClient', () => ({
  httpClient: {
    get: vi.fn(),
    put: vi.fn(),
  },
}))

vi.mock('../../locations/api/locationApi', () => ({
  locationApi: {
    list: vi.fn(),
  },
}))

vi.mock('../../../shared/config/env', () => ({
  isTechnicianMockEnabled: vi.fn(() => false),
}))

describe('technicianApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('list: calls GET /technicians with query parameters', async () => {
    const mockData = {
      content: [
        {
          technicianId: 3,
          fullName: 'Nguyễn Kỹ Thuật',
          email: 'tech1@nexora.campus',
          status: 'ACTIVE',
          available: true,
          maxActiveTickets: 5,
          version: 0,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    }
    httpClient.get.mockResolvedValueOnce({ data: mockData })

    const result = await technicianApi.list({
      page: 0,
      size: 20,
      query: 'tech1',
      available: 'true',
    })

    expect(httpClient.get).toHaveBeenCalledWith('/technicians', {
      params: { page: 0, size: 20, query: 'tech1', available: 'true' },
      signal: undefined,
    })
    expect(result).toEqual(mockData)
  })

  it('list: removes available when empty string or null', async () => {
    httpClient.get.mockResolvedValueOnce({ data: { content: [] } })

    await technicianApi.list({ page: 0, available: '' })

    expect(httpClient.get).toHaveBeenCalledWith('/technicians', {
      params: { page: 0 },
      signal: undefined,
    })
  })

  it('getById: calls GET /technicians/{id}', async () => {
    const mockDetail = {
      technicianId: 3,
      fullName: 'Nguyễn Kỹ Thuật',
      email: 'tech1@nexora.campus',
      available: true,
      maxActiveTickets: 5,
      version: 0,
      skills: [],
      serviceAreas: [],
    }
    httpClient.get.mockResolvedValueOnce({ data: mockDetail })

    const result = await technicianApi.getById(3)

    expect(httpClient.get).toHaveBeenCalledWith('/technicians/3', {
      signal: undefined,
    })
    expect(result).toEqual(mockDetail)
  })

  it('updateCapacity: calls PUT /technicians/{id} with sanitized payload', async () => {
    const updated = { technicianId: 3, available: false, maxActiveTickets: 7, version: 1 }
    httpClient.put.mockResolvedValueOnce({ data: updated })

    const result = await technicianApi.updateCapacity(3, {
      available: false,
      maxActiveTickets: 7,
      version: 0,
    })

    expect(httpClient.put).toHaveBeenCalledWith('/technicians/3', {
      available: false,
      maxActiveTickets: 7,
      version: 0,
    })
    expect(result).toEqual(updated)
  })

  it('getSkills: calls GET /skills?active=true (non-admin endpoint)', async () => {
    const mockSkills = [
      { id: 1, code: 'ELEC', name: 'Điện dân dụng & công nghiệp', active: true },
    ]
    httpClient.get.mockResolvedValueOnce({ data: mockSkills })

    const result = await technicianApi.getSkills()

    expect(httpClient.get).toHaveBeenCalledWith('/skills', {
      params: { active: true },
      signal: undefined,
    })
    expect(result).toEqual(mockSkills)
  })

  it('updateSkills: calls PUT /technicians/{id}/skills with sanitized payload', async () => {
    const updated = { technicianId: 3, version: 2 }
    httpClient.put.mockResolvedValueOnce({ data: updated })

    const payload = {
      skills: [
        { skillId: '1', proficiencyLevel: '4' },
        { skillId: 2, proficiencyLevel: 3 },
      ],
      version: 1,
    }

    const result = await technicianApi.updateSkills(3, payload)

    expect(httpClient.put).toHaveBeenCalledWith('/technicians/3/skills', {
      skills: [
        { skillId: 1, proficiencyLevel: 4 },
        { skillId: 2, proficiencyLevel: 3 },
      ],
      version: 1,
    })
    expect(result).toEqual(updated)
  })

  it('getLocations: calls locationApi.list with active=true', async () => {
    const mockLocations = [{ id: 1, code: 'CS1', name: 'Cơ sở 1', active: true }]
    locationApi.list.mockResolvedValueOnce(mockLocations)

    const result = await technicianApi.getLocations(null)

    expect(locationApi.list).toHaveBeenCalledWith(null, true, undefined)
    expect(result).toEqual(mockLocations)
  })

  it('updateServiceAreas: calls PUT /technicians/{id}/service-areas with sanitized payload', async () => {
    const updated = { technicianId: 3, version: 2 }
    httpClient.put.mockResolvedValueOnce({ data: updated })

    const result = await technicianApi.updateServiceAreas(3, {
      locationIds: ['1', 2],
      version: 1,
    })

    expect(httpClient.put).toHaveBeenCalledWith('/technicians/3/service-areas', {
      locationIds: [1, 2],
      version: 1,
    })
    expect(result).toEqual(updated)
  })

  describe('technicianError', () => {
    it('formats 409 conflict error', () => {
      const err = { response: { status: 409, data: { detail: 'Stale version.' } } }
      expect(formatTechnicianError(err)).toContain('xung đột phiên bản')
      expect(isConflictError(err)).toBe(true)
    })

    it('formats fieldErrors from 400', () => {
      const err = {
        response: {
          status: 400,
          data: {
            fieldErrors: {
              maxActiveTickets: 'Sức chứa tối đa phải lớn hơn 0',
            },
          },
        },
      }
      expect(formatTechnicianError(err)).toBe('Sức chứa tối đa phải lớn hơn 0')
    })

    it('formats 403 forbidden error', () => {
      const err = { response: { status: 403 } }
      expect(formatTechnicianError(err)).toContain('không có quyền')
    })

    it('formats 404 not found error', () => {
      const err = { response: { status: 404 } }
      expect(formatTechnicianError(err)).toContain('Không tìm thấy')
    })

    it('formats network error', () => {
      const err = new Error('Network Error')
      expect(formatTechnicianError(err)).toContain('mạng')
    })
  })
})
