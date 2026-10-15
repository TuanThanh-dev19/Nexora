import { httpClient } from '../../../shared/api/httpClient'

export const equipmentTypeApi = {
  /**
   * Lists equipment types.
   * When active=true (default), only active equipment types are returned.
   * When active=false, all equipment types (active + inactive) are returned.
   */
  async list(active = true, signal) {
    const response = await httpClient.get('/equipment-types', {
      params: { active: Boolean(active) },
      signal,
    })
    return response.data
  },

  /**
   * Creates a new EquipmentType. Requires ADMIN role.
   * Payload: { code, name, description }
   */
  async create(payload) {
    const safePayload = {
      code: payload.code?.trim(),
      name: payload.name?.trim(),
      ...(payload.description != null && payload.description !== ''
        ? { description: payload.description.trim() }
        : {}),
    }
    const response = await httpClient.post('/admin/equipment-types', safePayload)
    return response.data
  },

  /**
   * Updates an existing EquipmentType. Requires ADMIN role.
   * Payload: { name, description, active }
   * Note: 'code' and 'version' are intentionally excluded.
   */
  async update(id, payload) {
    const safePayload = {
      name: payload.name?.trim(),
      description:
        payload.description != null && payload.description !== ''
          ? payload.description.trim()
          : null,
      active: Boolean(payload.active),
    }
    const response = await httpClient.put(
      `/admin/equipment-types/${encodeURIComponent(id)}`,
      safePayload,
    )
    return response.data
  },
}
