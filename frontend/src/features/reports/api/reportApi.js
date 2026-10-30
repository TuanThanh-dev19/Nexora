import { httpClient } from '../../../shared/api/httpClient'

export const reportApi = {
  async getTicketReport(params = {}, { signal } = {}) {
    const response = await httpClient.get('/reports/tickets', {
      params,
      signal,
    })
    return response.data
  },

  async exportTicketReportCsv(params = {}, { signal } = {}) {
    const response = await httpClient.get('/reports/tickets/export', {
      params,
      signal,
      responseType: 'blob',
    })
    return response
  },
}

