import { httpClient } from '../../../shared/api/httpClient'
import { isTechnicianMockEnabled } from '../../../shared/config/env'
import { locationApi } from '../../locations/api/locationApi'
import { technicianMockAdapter } from '../mocks/technicianMock'

export const technicianApi = {
  /**
   * Danh sách kỹ thuật viên phân trang và lọc theo available, query (tên/email).
   * Endpoint: GET /api/v1/technicians
   * @param {Object} params - { page, size, query, available }
   * @param {AbortSignal} [signal]
   */
  async list(params = {}, signal) {
    if (isTechnicianMockEnabled()) {
      return technicianMockAdapter.listTechnicians(params)
    }
    const cleanParams = { ...params }
    if (cleanParams.available === '' || cleanParams.available == null) {
      delete cleanParams.available
    }
    const { data } = await httpClient.get('/technicians', {
      params: cleanParams,
      signal,
    })
    return data
  },

  /**
   * Chi tiết hồ sơ kỹ thuật viên.
   * Endpoint: GET /api/v1/technicians/{technicianId}
   * @param {number|string} technicianId
   * @param {AbortSignal} [signal]
   */
  async getById(technicianId, signal) {
    if (isTechnicianMockEnabled()) {
      return technicianMockAdapter.getTechnicianById(technicianId)
    }
    const { data } = await httpClient.get(
      `/technicians/${encodeURIComponent(technicianId)}`,
      { signal },
    )
    return data
  },

  /**
   * Cập nhật tính sẵn sàng và năng lực xử lý (capacity).
   * Endpoint: PUT /api/v1/technicians/{technicianId}
   * @param {number|string} technicianId
   * @param {{ available: boolean, maxActiveTickets: number, version?: number }} payload
   */
  async updateCapacity(technicianId, payload) {
    const safePayload = {
      available: Boolean(payload.available),
      maxActiveTickets: Number(payload.maxActiveTickets),
      ...(payload.version != null ? { version: Number(payload.version) } : {}),
    }

    if (isTechnicianMockEnabled()) {
      return technicianMockAdapter.updateProfile(technicianId, safePayload)
    }
    const { data } = await httpClient.put(
      `/technicians/${encodeURIComponent(technicianId)}`,
      safePayload,
    )
    return data
  },

  /**
   * Lấy danh mục kỹ năng đang hoạt động (không dùng endpoint admin).
   * Endpoint: GET /api/v1/skills?active=true
   * @param {AbortSignal} [signal]
   */
  async getSkills(signal) {
    if (isTechnicianMockEnabled()) {
      return technicianMockAdapter.getSkills()
    }
    const { data } = await httpClient.get('/skills', {
      params: { active: true },
      signal,
    })
    return data
  },

  /**
   * Cập nhật danh sách kỹ năng của kỹ thuật viên.
   * Endpoint: PUT /api/v1/technicians/{technicianId}/skills
   * @param {number|string} technicianId
   * @param {{ skills: Array<{ skillId: number, proficiencyLevel: number }>, version?: number }} payload
   */
  async updateSkills(technicianId, payload) {
    const safePayload = {
      skills: (payload.skills ?? []).map((s) => ({
        skillId: Number(s.skillId),
        proficiencyLevel: Number(s.proficiencyLevel),
      })),
      ...(payload.version != null ? { version: Number(payload.version) } : {}),
    }

    if (isTechnicianMockEnabled()) {
      return technicianMockAdapter.updateSkills(technicianId, safePayload)
    }
    const { data } = await httpClient.put(
      `/technicians/${encodeURIComponent(technicianId)}/skills`,
      safePayload,
    )
    return data
  },

  /**
   * Lấy danh sách địa điểm phân cấp (active=true).
   * Endpoint: GET /api/v1/locations?parentId={parentId}&active=true
   * @param {number|string|null} parentId
   * @param {AbortSignal} [signal]
   */
  async getLocations(parentId, signal) {
    if (isTechnicianMockEnabled()) {
      return technicianMockAdapter.getLocations(parentId)
    }
    return locationApi.list(parentId, true, signal)
  },

  /**
   * Cập nhật vùng phục vụ (Service Areas) của kỹ thuật viên.
   * Endpoint: PUT /api/v1/technicians/{technicianId}/service-areas
   * @param {number|string} technicianId
   * @param {{ locationIds: Array<number|string>, version?: number }} payload
   */
  async updateServiceAreas(technicianId, payload) {
    const safePayload = {
      locationIds: (payload.locationIds ?? []).map((id) => Number(id)),
      ...(payload.version != null ? { version: Number(payload.version) } : {}),
    }

    if (isTechnicianMockEnabled()) {
      return technicianMockAdapter.updateServiceAreas(technicianId, safePayload)
    }
    const { data } = await httpClient.put(
      `/technicians/${encodeURIComponent(technicianId)}/service-areas`,
      safePayload,
    )
    return data
  },
}
