import { httpClient } from '../../../shared/api/httpClient'

export const ticketApi = {
  async search(requestSearch, signal) {
    const params = new URLSearchParams(requestSearch)
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
  async eligibleTechnicians(ticketId, params, signal) {
    const response = await httpClient.get(
      `/tickets/${encodeURIComponent(ticketId)}/eligible-technicians`,
      { params, signal },
    )
    return response.data
  },
  async review(ticketId, payload) {
    const response = await httpClient.post(
      `/tickets/${encodeURIComponent(ticketId)}/review`,
      payload,
    )
    return response.data
  },
  async reject(ticketId, payload) {
    const response = await httpClient.post(
      `/tickets/${encodeURIComponent(ticketId)}/reject`,
      payload,
    )
    return response.data
  },
  async cancel(ticketId, payload) {
    const response = await httpClient.post(
      `/tickets/${encodeURIComponent(ticketId)}/cancel`,
      payload,
    )
    return response.data
  },
  async assign(ticketId, payload) {
    const response = await httpClient.post(
      `/tickets/${encodeURIComponent(ticketId)}/assign`,
      payload,
    )
    return response.data
  },
  async reassign(ticketId, payload) {
    const response = await httpClient.post(
      `/tickets/${encodeURIComponent(ticketId)}/reassign`,
      payload,
    )
    return response.data
  },
  async unassign(ticketId, payload) {
    const response = await httpClient.post(
      `/tickets/${encodeURIComponent(ticketId)}/unassign`,
      payload,
    )
    return response.data
  },
}
