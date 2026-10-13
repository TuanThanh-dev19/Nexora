import { httpClient } from '../../../shared/api/httpClient'

export const createTicketApi = {
  async categories(signal) {
    return (
      await httpClient.get('/categories', { params: { active: true }, signal })
    ).data
  },
  async form(id, signal) {
    return (
      await httpClient.get(
        `/categories/${encodeURIComponent(id)}/published-form`,
        { signal },
      )
    ).data
  },
  async locations(parentId, signal) {
    return (
      await httpClient.get('/locations', {
        params: { active: true, ...(parentId ? { parentId } : {}) },
        signal,
      })
    ).data
  },
  async assets(id, query, page, signal) {
    return (
      await httpClient.get(
        `/locations/${encodeURIComponent(id)}/eligible-assets`,
        { params: { query, page, size: 20 }, signal },
      )
    ).data
  },
  async submit(payload) {
    return (
      await httpClient.post('/tickets', payload, {
        headers: { 'Content-Type': undefined },
      })
    ).data
  },
}
