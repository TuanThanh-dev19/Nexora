import { httpClient } from '../../../shared/api/httpClient'

export const locationApi = {
  /**
   * Lists locations.
   * When parentId is null/undefined, returns root locations (CAMPUS).
   * When parentId is present, returns direct children.
   * When active is true, returns active records only.
   * When active is false, returns all records (active + inactive).
   */
  async list(parentId, active = true, signal) {
    let actualActive = active
    let actualSignal = signal

    // Handle legacy call: list(parentId, signal)
    if (
      active &&
      typeof active === 'object' &&
      ('aborted' in active || (typeof AbortSignal !== 'undefined' && active instanceof AbortSignal))
    ) {
      actualSignal = active
      actualActive = false
    }

    const params = { active: actualActive }
    if (parentId != null) {
      params.parentId = parentId
    }

    const response = await httpClient.get('/locations', {
      params,
      signal: actualSignal,
    })
    return response.data
  },

  /**
   * Returns details of a single location.
   */
  async getById(id, signal) {
    const response = await httpClient.get(
      `/locations/${encodeURIComponent(id)}`,
      { signal },
    )
    return response.data
  },

  /**
   * Creates a new Location. Requires ADMIN role.
   * Payload: { code, name, type, parentId, description }
   */
  async create(payload) {
    const safePayload = {
      code: payload.code?.trim(),
      name: payload.name?.trim(),
      type: payload.type,
      parentId: payload.parentId != null ? Number(payload.parentId) : null,
      ...(payload.description != null && payload.description !== ''
        ? { description: payload.description.trim() }
        : {}),
    }
    const response = await httpClient.post('/admin/locations', safePayload)
    return response.data
  },

  /**
   * Updates an existing Location. Requires ADMIN role.
   * Payload: { name, parentId, description, active }
   * Note: 'code' and 'version' are intentionally excluded.
   */
  async update(id, payload) {
    const safePayload = {
      name: payload.name?.trim(),
      parentId: payload.parentId != null ? Number(payload.parentId) : null,
      description:
        payload.description != null && payload.description !== ''
          ? payload.description.trim()
          : null,
      active: Boolean(payload.active),
    }
    const response = await httpClient.put(
      `/admin/locations/${encodeURIComponent(id)}`,
      safePayload,
    )
    return response.data
  },
}
