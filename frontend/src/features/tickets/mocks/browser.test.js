import { afterEach, describe, expect, it, vi } from 'vitest'
import { setupWorker } from 'msw/browser'
import { env } from '../../../shared/config/env'
import { ticketApi } from '../api/ticketApi'
import { startTicketWorkflowMock } from './browser'
import { setupServer } from 'msw/node'

vi.mock('msw/browser', () => ({ setupWorker: vi.fn() }))

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  delete window.ticketWorkflowMock
})

describe('browser mock bootstrap', () => {
  it('starts before exposing demo controls and preserves the real auth request and actor', async () => {
    const server = setupServer()
    const start = vi.fn().mockResolvedValue(undefined)
    setupWorker.mockImplementation((...handlers) => {
      server.use(...handlers)
      return { start }
    })
    server.listen({ onUnhandledRequest: 'error' })
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        id: 7,
        fullName: 'Actual session manager',
        status: 'ACTIVE',
        roles: ['MANAGER'],
      }),
    })
    vi.stubGlobal('fetch', fetch)
    sessionStorage.setItem('nexora.session.token', 'test-session-token')
    try {
      await startTicketWorkflowMock()
      expect(start).toHaveBeenCalledWith({ onUnhandledRequest: 'bypass' })
      expect(window.ticketWorkflowMock).toEqual({
        reset: expect.any(Function),
        failNextAction: expect.any(Function),
        failNextSubresource: expect.any(Function),
      })
      await ticketApi.review(10, { version: 0 })
      const detail = await ticketApi.getById(10)
      expect(detail.statusHistory.at(-1).changedBy).toEqual({
        id: 7,
        fullName: 'Actual session manager',
      })
      const authRequest = fetch.mock.calls[0][0]
      expect(authRequest.url).toBe(
        new URL(`${env.apiBaseUrl}/auth/me`, window.location.origin).href,
      )
      expect(authRequest.headers.get('Authorization')).toBe(
        'Bearer test-session-token',
      )
      expect(authRequest.headers.get('accept')).toContain('msw/passthrough')
      expect(authRequest.headers.get('accept')).toContain('application/json')
    } finally {
      sessionStorage.clear()
      server.close()
    }
  })
})
