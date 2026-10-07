import { httpClient } from '../../../shared/api/httpClient'

export const locationApi = {
  async list(parentId, signal) {
    const response = await httpClient.get('/locations', {
      params: { active: false, ...(parentId ? { parentId } : {}) },
      signal,
    })
    return response.data
  },
  async getById(id, signal) {
    const response = await httpClient.get(
      `/locations/${encodeURIComponent(id)}`,
      { signal },
    )
    return response.data
  },
}
