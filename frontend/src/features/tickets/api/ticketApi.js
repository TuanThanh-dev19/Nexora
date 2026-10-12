import { httpClient } from '../../../shared/api/httpClient'
import { env } from '../../../shared/config/env'

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
  async assignedToMe(requestSearch, signal) {
    const response = await httpClient.get('/technicians/me/assigned-tickets', {
      params: new URLSearchParams(requestSearch),
      signal,
    })
    return response.data
  },
  async start(ticketId, payload) {
    const response = await httpClient.post(
      `/tickets/${encodeURIComponent(ticketId)}/start`,
      payload,
    )
    return response.data
  },
  async workLogs(ticketId, signal) {
    const response = await httpClient.get(
      `/tickets/${encodeURIComponent(ticketId)}/work-logs`,
      { signal },
    )
    return response.data
  },
  async addWorkLog(ticketId, payload) {
    const response = await httpClient.post(
      `/tickets/${encodeURIComponent(ticketId)}/work-logs`,
      payload,
    )
    return response.data
  },
  async uploadWorkResult(ticketId, files) {
    const form = new FormData()
    form.append('metadata', JSON.stringify({ attachmentType: 'WORK_RESULT' }))
    files.forEach((file) => form.append('files', file))
    const token = sessionStorage.getItem('nexora.session.token')
    const response = await fetch(
      `${env.apiBaseUrl.replace(/\/$/, '')}/tickets/${encodeURIComponent(ticketId)}/attachments`,
      {
        method: 'POST',
        body: form,
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      },
    )
    const data = await response.json()
    if (response.status === 401)
      window.dispatchEvent(new Event('nexora:unauthorized'))
    if (!response.ok) {
      const error = new Error(data.detail ?? 'Không thể tải ảnh kết quả.')
      error.response = { status: response.status, data }
      throw error
    }
    return data
  },
  async resolve(ticketId, payload) {
    const response = await httpClient.post(
      `/tickets/${encodeURIComponent(ticketId)}/resolve`,
      payload,
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
