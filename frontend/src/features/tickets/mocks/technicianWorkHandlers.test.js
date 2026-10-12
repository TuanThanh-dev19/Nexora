import { setupServer } from 'msw/node'
import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'
import { ticketApi } from '../api/ticketApi'
import { createTicketWorkflowMock } from './ticketWorkflowHandlers'

let actor = {
  id: 21,
  fullName: 'Nguyễn An',
  status: 'ACTIVE',
  roles: ['TECHNICIAN'],
}
const mock = createTicketWorkflowMock({ getActor: () => actor })
const server = setupServer(...mock.handlers)
const ticket = (id) => mock.store.tickets.find((item) => item.id === id)
const fails = (request, status) =>
  expect(request).rejects.toMatchObject({ response: { status } })

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  mock.reset()
  actor = {
    id: 21,
    fullName: 'Nguyễn An',
    status: 'ACTIVE',
    roles: ['TECHNICIAN'],
  }
})
afterEach(() => {
  server.resetHandlers()
})
afterAll(() => server.close())

describe('technician work mock contract', () => {
  it('binds browser demo assignments to real authenticated technician IDs', async () => {
    const browserMock = createTicketWorkflowMock({
      getActor: () => actor,
      adaptTechnicianIds: true,
    })
    server.use(...browserMock.handlers)
    actor = {
      id: 7,
      fullName: 'Kỹ thuật viên A',
      status: 'ACTIVE',
      roles: ['TECHNICIAN'],
    }
    expect(
      (await ticketApi.assignedToMe('')).content[0].currentAssignment
        .technician,
    ).toMatchObject({ technicianId: 7, fullName: 'Kỹ thuật viên A' })
    actor = {
      id: 8,
      fullName: 'Kỹ thuật viên B',
      status: 'ACTIVE',
      roles: ['TECHNICIAN'],
    }
    expect(
      (await ticketApi.assignedToMe('')).content.map((item) => item.id),
    ).toEqual([13])
    actor = {
      id: 7,
      fullName: 'Kỹ thuật viên A',
      status: 'ACTIVE',
      roles: ['TECHNICIAN'],
    }
    expect(
      (await ticketApi.assignedToMe('')).content.map((item) => item.id),
    ).toEqual([12])
  })

  it('counts only active assignments, with filters and pagination', async () => {
    const result = await ticketApi.assignedToMe(
      'page=0&size=1&sort=ticketNumber,asc',
    )
    expect(result.totalElements).toBe(1)
    expect(result.content.map((item) => item.id)).toEqual([12])
    expect(result.content[0].currentAssignment.technician.technicianId).toBe(21)
    expect(
      (await ticketApi.search('')).content.some((item) => item.id === 15),
    ).toBe(true)
    expect(
      (await ticketApi.assignedToMe('status=IN_PROGRESS')).totalElements,
    ).toBe(0)
    await fails(ticketApi.assignedToMe('status=RESOLVED'), 400)
    actor = {
      id: 24,
      fullName: 'Phạm Dũng',
      status: 'ACTIVE',
      roles: ['TECHNICIAN'],
    }
    expect(
      (await ticketApi.assignedToMe('')).content.map((item) => item.id),
    ).toEqual([13])
  })

  it('starts, records a log and resolves with matching version', async () => {
    await fails(ticketApi.start(12, { version: 99 }), 409)
    expect(await ticketApi.start(12, { version: 2 })).toMatchObject({
      status: 'IN_PROGRESS',
      version: 3,
    })
    expect(ticket(12).currentAssignment.acceptedAt).not.toBeNull()
    await fails(
      ticketApi.resolve(12, {
        version: 3,
        resolutionOutcome: 'FIXED',
        resolutionSummary: 'Đã xử lý',
      }),
      422,
    )
    const log = await ticketApi.addWorkLog(12, {
      version: 3,
      logType: 'REPAIR',
      content: ' Đã sửa máy ',
      startedAt: '2026-10-09T09:00:00+07:00',
      endedAt: '2026-10-09T09:20:00+07:00',
      minutesSpent: 20,
    })
    expect(log).toMatchObject({ content: 'Đã sửa máy', ticketVersion: 4 })
    expect((await ticketApi.workLogs(12)).map((item) => item.id)).toEqual([
      log.id,
    ])
    const former = ticket(12).currentAssignment
    const resolved = await ticketApi.resolve(12, {
      version: 4,
      resolutionOutcome: 'FIXED',
      resolutionSummary: ' Đã xử lý ',
    })
    expect(resolved).toMatchObject({
      status: 'RESOLVED',
      version: 5,
      currentAssignment: null,
    })
    expect(former.endedAt).not.toBeNull()
    expect(ticket(12).statusHistory.at(-1).transitionAction).toBe('RESOLVE')
    expect((await ticketApi.assignedToMe('')).totalElements).toBe(0)
    expect((await ticketApi.getById(12)).resolutionSummary).toBe('Đã xử lý')
  })

  it('rejects invalid times, minutes, outcome and stale writes without changing work', async () => {
    await ticketApi.start(12, { version: 2 })
    const input = {
      version: 3,
      logType: 'PROGRESS',
      content: 'Kiểm tra',
      startedAt: '2026-10-09T10:00:00+07:00',
      endedAt: '2026-10-09T09:00:00+07:00',
      minutesSpent: 0,
    }
    await fails(ticketApi.addWorkLog(12, input), 400)
    expect(
      mock.store.workLogs.filter((item) => item.ticketId === 12),
    ).toHaveLength(0)
    await fails(
      ticketApi.addWorkLog(12, {
        ...input,
        version: 2,
        endedAt: null,
        minutesSpent: 1,
      }),
      409,
    )
    await fails(
      ticketApi.resolve(12, {
        version: 3,
        resolutionOutcome: 'DUPLICATE',
        resolutionSummary: 'X',
      }),
      422,
    )
  })

  it('keeps historical visibility read-only and invalidates lost assignments', async () => {
    await fails(ticketApi.start(15, { version: ticket(15).version }), 422)
    await fails(ticketApi.start(13, { version: ticket(13).version }), 404)
    mock.failNextAction(403)
    await fails(ticketApi.start(12, { version: 2 }), 403)
    expect((await ticketApi.assignedToMe('')).totalElements).toBe(0)
    expect((await ticketApi.getById(12)).currentAssignment).toBeNull()
    actor = {
      id: 4,
      fullName: 'Người báo demo',
      status: 'ACTIVE',
      roles: ['REQUESTER'],
    }
    await fails(ticketApi.assignedToMe(''), 403)
    await fails(ticketApi.start(12, { version: 2 }), 403)
  })
})
