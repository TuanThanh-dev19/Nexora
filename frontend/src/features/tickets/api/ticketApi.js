import { httpClient } from '../../../shared/api/httpClient'

export const ticketApi = {
  async search(params, signal) {
    const response = await httpClient.get('/tickets', { params, signal })
    return response.data
  },
  async getById(ticketId, signal) {
    const response = await httpClient.get(
      `/tickets/${encodeURIComponent(ticketId)}`,
      { signal },
    )
    return response.data
  },
}
