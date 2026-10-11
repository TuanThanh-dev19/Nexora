import { delay, http, HttpResponse } from 'msw'

export const DASHBOARD_SCENARIOS = [
  'success',
  'zero',
  'loading',
  'error',
  'retry',
]

export function createDashboardMock(baseUrl = '/api/v1') {
  let scenario = 'success'
  let attempts = 0
  return {
    setScenario(value) {
      if (!DASHBOARD_SCENARIOS.includes(value))
        throw new TypeError('Unknown dashboard scenario')
      scenario = value
      attempts = 0
    },
    handlers: [
      http.get(`${baseUrl.replace(/\/$/, '')}/dashboard/summary`, async () => {
        const current = scenario
        const attempt = ++attempts
        if (current === 'loading') await delay('infinite')
        else await delay(200)
        if (current === 'error' || (current === 'retry' && attempt === 1)) {
          return HttpResponse.json(
            { title: 'Mock unavailable' },
            { status: 503 },
          )
        }
        const zero = current === 'zero'
        return HttpResponse.json({
          openTickets: zero ? 0 : 12,
          inProgressTickets: zero ? 0 : 4,
          slaAtRiskTickets: zero ? 0 : 2,
          resolvedThisWeek: zero ? 0 : 7,
          generatedAt: '2026-10-15T03:30:00Z',
        })
      }),
    ],
  }
}
