import { beforeEach, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { dashboardApi } from './dashboardApi'

vi.mock('../../../shared/api/httpClient', () => ({
  httpClient: { get: vi.fn() },
}))
beforeEach(() => vi.clearAllMocks())
const summary = {
  openTickets: 0,
  inProgressTickets: 0,
  slaAtRiskTickets: 0,
  resolvedThisWeek: 0,
  generatedAt: '2026-10-15T10:30:00+07:00',
}

it('calls the existing summary contract with cancellation support and no scope body', async () => {
  httpClient.get.mockResolvedValue({ data: summary })
  const signal = new AbortController().signal
  expect(await dashboardApi.getSummary({ signal })).toEqual(summary)
  expect(httpClient.get).toHaveBeenCalledWith('/dashboard/summary', { signal })
})

it.each([
  { openTickets: -1 },
  { inProgressTickets: 0.5 },
  { slaAtRiskTickets: '2' },
  { resolvedThisWeek: Number.MAX_SAFE_INTEGER + 1 },
  { generatedAt: 'October 15 2026' },
  { generatedAt: '2026-10-15T10:30:00' },
  { generatedAt: 'invalid' },
  { generatedAt: null },
])(
  'rejects invalid contract data instead of showing fake defaults: %s',
  async (invalid) => {
    httpClient.get.mockResolvedValue({ data: { ...summary, ...invalid } })
    await expect(dashboardApi.getSummary()).rejects.toThrow(TypeError)
  },
)
