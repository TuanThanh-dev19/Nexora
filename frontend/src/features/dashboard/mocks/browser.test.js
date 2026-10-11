import { afterEach, expect, it, vi } from 'vitest'
import { setupWorker } from 'msw/browser'
import { startDashboardMock } from './browser'
import { isDashboardMockEnabled } from '../../../shared/config/env'

vi.mock('msw/browser', () => ({ setupWorker: vi.fn() }))
afterEach(() => {
  vi.unstubAllEnvs()
  vi.clearAllMocks()
  delete window.dashboardMock
})

it('never starts dashboard mocks in production even with the flag enabled', async () => {
  vi.stubEnv('DEV', false)
  vi.stubEnv('VITE_DASHBOARD_MOCK', 'true')
  expect(isDashboardMockEnabled()).toBe(false)
  await startDashboardMock()
  expect(setupWorker).not.toHaveBeenCalled()
  expect(window.dashboardMock).toBeUndefined()
})

it('starts only with an explicit development flag', async () => {
  vi.stubEnv('DEV', true)
  vi.stubEnv('VITE_DASHBOARD_MOCK', 'true')
  vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'false')
  const start = vi.fn().mockResolvedValue(undefined)
  setupWorker.mockReturnValue({ start })
  await startDashboardMock()
  expect(start).toHaveBeenCalledWith({ onUnhandledRequest: 'bypass' })
  expect(window.dashboardMock.setScenario).toBeTypeOf('function')
  expect(() => window.dashboardMock.setScenario('unknown')).toThrow()
})
