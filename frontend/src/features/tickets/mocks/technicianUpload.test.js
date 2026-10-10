// @vitest-environment node
import { File } from 'node:buffer'
import { setupServer } from 'msw/node'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { ticketApi } from '../api/ticketApi'
import { createTicketWorkflowMock } from './ticketWorkflowHandlers'

vi.mock('../../../shared/config/env', () => ({
  env: { apiBaseUrl: 'http://localhost/api/v1' },
}))

const actor = {
  id: 24,
  fullName: 'Phạm Dũng',
  status: 'ACTIVE',
  roles: ['TECHNICIAN'],
}
const mock = createTicketWorkflowMock({ getActor: () => actor })
const server = setupServer(...mock.handlers)

beforeAll(() => {
  vi.stubGlobal('sessionStorage', { getItem: () => null })
  server.listen({ onUnhandledRequest: 'error' })
})
afterAll(() => {
  server.close()
  vi.unstubAllGlobals()
})

describe('work result multipart contract', () => {
  it('accepts a JPEG/PNG file and publishes only attachment metadata', async () => {
    const result = await ticketApi.uploadWorkResult(13, [
      new File(['image'], 'result.png', { type: 'image/png' }),
    ])
    expect(result).toMatchObject([
      { attachmentType: 'WORK_RESULT', originalName: 'result.png' },
    ])
    expect(result[0]).not.toHaveProperty('storageKey')
    expect(
      mock.store.tickets.find((ticket) => ticket.id === 13).attachments,
    ).toHaveLength(1)
  })

  it('rejects an unsupported image without changing attachments', async () => {
    await expect(
      ticketApi.uploadWorkResult(13, [
        new File(['image'], 'result.gif', { type: 'image/gif' }),
      ]),
    ).rejects.toMatchObject({ response: { status: 400 } })
    expect(
      mock.store.tickets.find((ticket) => ticket.id === 13).attachments,
    ).toHaveLength(1)
  })
})
