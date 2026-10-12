import { httpClient } from '../../../shared/api/httpClient'

const METRIC_FIELDS = [
  'openTickets',
  'inProgressTickets',
  'slaAtRiskTickets',
  'resolvedThisWeek',
]

function parseDashboardSummary(payload) {
  const hasValidMetrics = METRIC_FIELDS.every(
    (field) => Number.isInteger(payload?.[field]) && payload[field] >= 0,
  )
  const hasGeneratedAt =
    typeof payload?.generatedAt === 'string' &&
    !Number.isNaN(Date.parse(payload.generatedAt))

  if (!hasValidMetrics || !hasGeneratedAt) {
    throw new TypeError(
      'Dashboard summary response does not match the contract.',
    )
  }

  return payload
}

export const dashboardApi = {
  async getSummary() {
    const response = await httpClient.get('/dashboard/summary')
    return parseDashboardSummary(response.data)
  },
}
