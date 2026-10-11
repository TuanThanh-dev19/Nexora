import { setupWorker } from 'msw/browser'
import {
  env,
  isDashboardMockEnabled,
  isTicketWorkflowMockEnabled,
} from '../../../shared/config/env'
import { createDashboardMock } from './dashboardHandlers'

export async function startDashboardMock() {
  if (!isDashboardMockEnabled()) return
  const mock = createDashboardMock(env.apiBaseUrl)
  if (isTicketWorkflowMockEnabled()) {
    const { startTicketWorkflowMock } =
      await import('../../tickets/mocks/browser')
    await startTicketWorkflowMock(mock.handlers)
  } else {
    const worker = setupWorker(...mock.handlers)
    await worker.start({ onUnhandledRequest: 'bypass' })
  }
  window.dashboardMock = { setScenario: mock.setScenario }
}
