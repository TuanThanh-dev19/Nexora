import { httpClient } from '../../../shared/api/httpClient'

const METRIC_FIELDS = [
  'openTickets',
  'inProgressTickets',
  'slaAtRiskTickets',
  'resolvedThisWeek',
]

function parseDashboardSummary(payload) {
  const hasValidMetrics = METRIC_FIELDS.every(
    (field) => Number.isSafeInteger(payload?.[field]) && payload[field] >= 0,
  )
  const hasGeneratedAt =
    typeof payload?.generatedAt === 'string' &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(
      payload.generatedAt,
    ) &&
    !Number.isNaN(Date.parse(payload.generatedAt))

  if (!hasValidMetrics || !hasGeneratedAt) {
    throw new TypeError(
      'Dashboard summary response does not match the contract.',
    )
  }

  return payload
}

export const dashboardApi = {
  async getSummary({ signal } = {}) {
    const response = await httpClient.get('/dashboard/summary', { signal })
    return parseDashboardSummary(response.data)
  },
}
