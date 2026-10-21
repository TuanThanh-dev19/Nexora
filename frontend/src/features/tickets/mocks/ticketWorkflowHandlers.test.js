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
import { httpClient } from '../../../shared/api/httpClient'
import { createTicketWorkflowMock } from './ticketWorkflowHandlers'
import { WORKFLOW_MOCK_MANAGER } from './ticketWorkflowFixtures'

let actor = WORKFLOW_MOCK_MANAGER
const mock = createTicketWorkflowMock({ getActor: () => actor })
const server = setupServer(...mock.handlers)
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  mock.reset()
  actor = WORKFLOW_MOCK_MANAGER
})
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

const ticket = (id) => mock.store.tickets.find((item) => item.id === id)
const expectFailure = (request, status) =>
  expect(request).rejects.toMatchObject({ response: { status } })

describe('ticket workflow mock HTTP contract', () => {
  it('keeps status, version, history, assignments, list and workload consistent through a workflow', async () => {
    expect(await ticketApi.review(10, { version: 0 })).toMatchObject({
      status: 'UNDER_REVIEW',
      version: 1,
    })
    await ticketApi.assign(10, { version: 1, technicianId: 21 })
    const originalAssignment = ticket(10).currentAssignment
    await ticketApi.reassign(10, {
      version: 2,
      technicianId: 29,
      reason: 'Đổi người',
    })
    expect(originalAssignment).toMatchObject({ endReason: 'Đổi người' })
    expect(originalAssignment.endedAt).not.toBeNull()
    expect(ticket(10).currentAssignment).toMatchObject({
      technician: { technicianId: 29 },
      acceptedAt: null,
    })
    expect(ticket(10).statusHistory.at(-1)).toMatchObject({
      fromStatus: 'ASSIGNED',
      toStatus: 'ASSIGNED',
      transitionAction: 'REASSIGN',
      reason: 'Đổi người',
    })
    await ticketApi.unassign(10, { version: 3, reason: 'Chờ xem xét' })
    const detail = await ticketApi.getById(10)
    expect(detail).toMatchObject({
      status: 'UNDER_REVIEW',
      version: 4,
      currentAssignment: null,
    })
    expect(detail.statusHistory.map((entry) => entry.transitionAction)).toEqual(
      ['SUBMIT', 'BEGIN_REVIEW', 'ASSIGN', 'REASSIGN', 'UNASSIGN'],
    )
    expect(detail.updatedAt).toBe(detail.statusHistory.at(-1).changedAt)
    const list = await ticketApi.search('status=UNDER_REVIEW')
    expect(list.content.find((item) => item.id === 10)).toMatchObject({
      status: 'UNDER_REVIEW',
      updatedAt: detail.updatedAt,
    })
    expect(list.content[0]).not.toHaveProperty('statusHistory')
    const candidates = await ticketApi.eligibleTechnicians(10, {
      page: 0,
      size: 20,
    })
    expect(
      candidates.content.find((item) => item.technicianId === 29)
        .activeTicketCount,
    ).toBe(0)
    expect(
      mock.store.assignments.filter(
        (item) => item.ticketId === 10 && item.endedAt == null,
      ),
    ).toHaveLength(0)
  })
  it('reassigns IN_PROGRESS to ASSIGNED and releases the old technician capacity', async () => {
    const original = ticket(13).currentAssignment
    expect(original.acceptedAt).not.toBeNull()
    await ticketApi.reassign(13, {
      version: ticket(13).version,
      technicianId: 21,
      reason: 'Đổi người xử lý',
    })
    expect(ticket(13).status).toBe('ASSIGNED')
    expect(ticket(13).currentAssignment.acceptedAt).toBeNull()
    expect(original.endedAt).not.toBeNull()
    const candidates = await ticketApi.eligibleTechnicians(13, {
      page: 0,
      size: 20,
    })
    expect(
      candidates.content.find((item) => item.technicianId === 24),
    ).toMatchObject({ activeTicketCount: 0, eligibilityReasons: [] })
  })
  it.each([
    [10, 'CANCEL_BEFORE_ASSIGNMENT'],
    [12, 'CANCEL_AFTER_ASSIGNMENT'],
    [13, 'CANCEL_AFTER_ASSIGNMENT'],
    [14, 'CANCEL_AFTER_ASSIGNMENT'],
  ])(
    'cancels ticket %s with canonical history and ends any active assignment',
    async (id, action) => {
      const active = ticket(id).currentAssignment
      await ticketApi.cancel(id, {
        version: ticket(id).version,
        reason: 'Không còn nhu cầu',
      })
      expect(ticket(id)).toMatchObject({
        status: 'CANCELLED',
        currentAssignment: null,
      })
      expect(ticket(id).statusHistory.at(-1).transitionAction).toBe(action)
      if (active)
        expect(active).toMatchObject({
          endReason: 'Không còn nhu cầu',
          endedAt: expect.any(String),
        })
    },
  )
  it('rejects with a reason and assigns a reopened ticket', async () => {
    await ticketApi.reject(10, { version: 0, reason: 'Không hợp lệ' })
    expect(ticket(10)).toMatchObject({ status: 'REJECTED', version: 1 })
    await ticketApi.assign(14, {
      version: ticket(14).version,
      technicianId: 21,
    })
    expect(ticket(14)).toMatchObject({
      status: 'ASSIGNED',
      currentAssignment: { technician: { technicianId: 21 } },
    })
  })
  it('returns capacity failure without override and rejects assignment when at capacity', async () => {
    const before = structuredClone(mock.store)
    await expectFailure(
      ticketApi.assign(11, { version: 1, technicianId: 24 }),
      422,
    )
    expect(mock.store).toEqual(before)
  })
  it.each([22, 23, 24, 25, 26, 27, 28])(
    'never assigns blocked candidate %s',
    async (technicianId) => {
      const before = structuredClone(mock.store)
      await expectFailure(
        ticketApi.assign(11, {
          version: 1,
          technicianId,
        }),
        422,
      )
      expect(mock.store).toEqual(before)
    },
  )
  it.each([400, 403, 404])(
    'simulates HTTP %s without changing fixtures',
    async (status) => {
      const before = structuredClone(mock.store)
      mock.failNextAction(status)
      await expectFailure(ticketApi.review(10, { version: 0 }), status)
      expect(mock.store).toEqual(before)
    },
  )
  it('models a competing action for 409 and returns the latest version on reload', async () => {
    mock.failNextAction(409)
    await expectFailure(ticketApi.review(10, { version: 0 }), 409)
    expect(await ticketApi.getById(10)).toMatchObject({
      status: 'UNDER_REVIEW',
      version: 1,
    })
    await expectFailure(ticketApi.review(10, { version: 0 }), 409)
    expect(ticket(10).statusHistory).toHaveLength(2)
  })
  it('allows one of two simultaneous actions with the same expected version', async () => {
    const results = await Promise.allSettled([
      ticketApi.review(10, { version: 0 }),
      ticketApi.review(10, { version: 0 }),
    ])
    expect(
      results.filter((result) => result.status === 'fulfilled'),
    ).toHaveLength(1)
    expect(
      results.find((result) => result.status === 'rejected').reason.response
        .status,
    ).toBe(409)
    expect(ticket(10).version).toBe(1)
    expect(ticket(10).statusHistory).toHaveLength(2)
  })
  it('returns real validation, permission, not-found and invalid-transition problems', async () => {
    await expectFailure(ticketApi.reject(10, { version: 0, reason: '  ' }), 400)
    await expectFailure(ticketApi.review(10, {}), 400)
    await expectFailure(
      ticketApi.review(10, { version: 0, status: 'CLOSED' }),
      400,
    )
    await expectFailure(ticketApi.review(999, { version: 0 }), 404)
    await expectFailure(
      ticketApi.review(18, { version: ticket(18).version }),
      422,
    )
    actor = { id: 4, status: 'ACTIVE', roles: ['REQUESTER'] }
    await expectFailure(ticketApi.review(10, { version: 0 }), 403)
    await expectFailure(ticketApi.eligibleTechnicians(10, {}), 403)
    expect(ticket(10).status).toBe('SUBMITTED')
  })
  it('searches, filters and paginates candidates with the shared envelope', async () => {
    const first = await ticketApi.eligibleTechnicians(11, { page: 0, size: 5 })
    expect(first).toMatchObject({
      page: 0,
      size: 5,
      totalElements: 9,
      totalPages: 2,
    })
    expect(first.content).toHaveLength(5)
    const second = await ticketApi.eligibleTechnicians(11, { page: 1, size: 5 })
    expect(second.content.map((item) => item.technicianId)).toEqual([
      26, 27, 28, 29,
    ])
    const matches = await ticketApi.eligibleTechnicians(11, {
      query: 'TRẦN',
      eligibility: 'BLOCKED',
    })
    expect(matches.content.map((item) => item.technicianId)).toEqual([22])
    expect(
      (await ticketApi.eligibleTechnicians(11, { query: 'no matches' }))
        .totalElements,
    ).toBe(0)
    expect(
      (await ticketApi.eligibleTechnicians(11, { eligibility: 'BLOCKED' }))
        .content,
    ).toHaveLength(7)
    await expectFailure(ticketApi.eligibleTechnicians(11, { query: '' }), 400)
    await expectFailure(
      ticketApi.eligibleTechnicians(11, { eligibility: 'OVERRIDE_REQUIRED' }),
      400,
    )
    await expectFailure(
      ticketApi.eligibleTechnicians(11, { eligibility: 'UNKNOWN' }),
      400,
    )
    await expectFailure(ticketApi.eligibleTechnicians(11, { size: 101 }), 400)
    await expectFailure(
      httpClient.get('/tickets/11/eligible-technicians?page=0&page=1'),
      400,
    )
  })
  it('uses canonical list filters even after all submitted tickets change state', async () => {
    await ticketApi.review(10, { version: 0 })
    expect((await ticketApi.search('status=SUBMITTED')).content).toEqual([])
    expect((await ticketApi.search('priority=NORMAL')).content).toEqual([])
    await expectFailure(ticketApi.search('priority=MEDIUM'), 400)
  })
  it('keeps technician historical visibility and requester ownership', async () => {
    actor = { id: 21, status: 'ACTIVE', roles: ['TECHNICIAN'] }
    expect(await ticketApi.getById(14)).toMatchObject({
      id: 14,
      currentAssignment: null,
    })
    await expectFailure(ticketApi.getById(10), 404)
    actor = { id: 99, status: 'ACTIVE', roles: ['REQUESTER'] }
    expect((await ticketApi.search('')).content).toEqual([])
    await expectFailure(ticketApi.getById(10), 404)
  })
})
