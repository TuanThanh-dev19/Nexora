import { bypass } from 'msw'
import { setupWorker } from 'msw/browser'
import { env } from '../../../shared/config/env'
import { createTicketWorkflowMock } from './ticketWorkflowHandlers'

export async function startTicketWorkflowMock(additionalHandlers = []) {
  const baseUrl = env.apiBaseUrl.replace(/\/$/, '')
  const mock = createTicketWorkflowMock({
    baseUrl,
    actionDelay: 300,
    adaptTechnicianIds: true,
    // Keep real authentication. This identity is used only for demo visibility/history.
    getActor: async (request) => {
      const authRequest = new Request(
        new URL(`${baseUrl}/auth/me`, window.location.origin),
        {
          headers: {
            Accept: 'application/json',
            Authorization: request.headers.get('Authorization') ?? '',
          },
        },
      )
      const response = await fetch(bypass(authRequest))
      return response.ok ? response.json() : null
    },
  })
  const worker = setupWorker(...mock.handlers, ...additionalHandlers)
  await worker.start({ onUnhandledRequest: 'bypass' })
  // Explicit development controls; no fake identity or mutations in production.
  window.ticketWorkflowMock = {
    reset: mock.reset,
    failNextAction: mock.failNextAction,
  }
}
