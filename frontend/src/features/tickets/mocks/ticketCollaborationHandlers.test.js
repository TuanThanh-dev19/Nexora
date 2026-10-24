import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'
import { setupServer } from 'msw/node'
import { ticketApi } from '../api/ticketApi'
import { httpClient } from '../../../shared/api/httpClient'
import { createTicketWorkflowMock } from './ticketWorkflowHandlers'

const owner = {
  id: 4,
  fullName: 'Người báo',
  status: 'ACTIVE',
  roles: ['REQUESTER'],
}
const manager = {
  id: 5,
  fullName: 'Quản lý',
  status: 'ACTIVE',
  roles: ['MANAGER'],
}
let actor = owner
const mock = createTicketWorkflowMock({ getActor: () => actor })
const server = setupServer(...mock.handlers)
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  mock.reset()
  mock.store.feedback = []
  actor = owner
})
afterEach(() => {
  sessionStorage.clear()
  server.resetHandlers()
  server.events.removeAllListeners()
})
afterAll(() => server.close())
const ticket = (id) => mock.store.tickets.find((item) => item.id === id)
const failure = async (promise, status) =>
  expect(promise).rejects.toMatchObject({ response: { status } })

describe('collaboration HTTP contract', () => {
  it.each([10, 11])(
    'records a manager competing transition when Requester cancellation conflicts on ticket %s',
    async (id) => {
      const before = ticket(id).version
      mock.failNextAction(409)
      await failure(
        ticketApi.cancel(id, {
          version: before,
          reason: 'Không cần hỗ trợ nữa',
        }),
        409,
      )
      const detail = await ticketApi.getById(id)
      expect(detail.version).toBe(before + 1)
      expect(detail.status).toBe(id === 10 ? 'UNDER_REVIEW' : 'ASSIGNED')
      expect(detail.statusHistory.at(-1)).toMatchObject({
        transitionAction: id === 10 ? 'BEGIN_REVIEW' : 'ASSIGN',
        changedBy: { id: manager.id },
        toStatus: detail.status,
      })
      expect(detail.statusHistory.at(-1).changedBy.id).not.toBe(owner.id)
      if (id === 11)
        expect(detail.currentAssignment.assignedBy.id).toBe(manager.id)
    },
  )
  it('returns corrupted image content once and valid bytes on the next request', async () => {
    const bytes = [...mock.store.attachmentContents['10:1000'].bytes]
    mock.failNextSubresource('attachments', 'invalid-image')
    expect(await (await ticketApi.attachmentContent(10, 1000)).text()).toBe(
      'Invalid PNG content',
    )
    const retry = await ticketApi.attachmentContent(10, 1000)
    expect(Array.from(new Uint8Array(await retry.arrayBuffer()))).toEqual(bytes)
    expect(mock.store.attachmentContents['10:1000'].bytes).toEqual(bytes)
    expect(() =>
      mock.failNextSubresource('comments', 'invalid-image'),
    ).toThrow()
  })
  it('seeds RESOLVED/CLOSED/REOPENED with consistent assignment, log and feedback history', () => {
    mock.reset()
    for (const id of [14, 15, 17]) {
      const detail = ticket(id)
      const resolved = detail.statusHistory.find(
        (entry) => entry.transitionAction === 'RESOLVE',
      )
      const log = mock.store.workLogs.find((item) => item.ticketId === id)
      expect(Date.parse(log.createdAt)).toBeLessThanOrEqual(
        Date.parse(resolved.changedAt),
      )
      expect(detail.currentAssignment).toBeNull()
      expect(detail.statusHistory.at(-1).toStatus).toBe(detail.status)
    }
    expect(ticket(14).resolvedAt).toBeNull()
    expect(ticket(17).closedAt).toBe(ticket(17).statusHistory.at(-1).changedAt)
    expect(Date.parse(mock.store.feedback[0].createdAt)).toBeGreaterThanOrEqual(
      Date.parse(ticket(17).closedAt),
    )
  })
  it('filters internal records before pagination/counting and preserves opaque IDs', async () => {
    const page = await ticketApi.comments(10, { page: 0, size: 1 })
    expect(page).toMatchObject({
      totalElements: 1,
      totalPages: 1,
      content: [{ id: 'comment-public', visibility: 'PUBLIC' }],
    })
    expect(JSON.stringify(page)).not.toContain('nội bộ')
    expect(
      (await ticketApi.comments(10, { page: 1, size: 1 })).content,
    ).toEqual([])
    actor = manager
    expect(
      (await ticketApi.comments(10, { page: 0, size: 20 })).totalElements,
    ).toBe(2)
    actor = { ...owner, roles: ['REQUESTER', 'MANAGER'] }
    expect(
      (await ticketApi.comments(10, { page: 0, size: 20 })).content.some(
        (item) => item.visibility === 'INTERNAL',
      ),
    ).toBe(true)
  })
  it('creates public comments, edits only content and keeps ticket version unchanged', async () => {
    const before = ticket(10).version
    const comment = await ticketApi.addComment(10, {
      content: '  Xin hỗ trợ  ',
      visibility: 'PUBLIC',
    })
    expect(comment).toMatchObject({
      author: { id: 4 },
      content: 'Xin hỗ trợ',
      editedAt: null,
    })
    expect(comment).not.toHaveProperty('ticketId')
    const edited = await ticketApi.updateComment(10, comment.id, {
      content: 'Đã bổ sung',
    })
    expect(edited).toMatchObject({
      content: 'Đã bổ sung',
      visibility: 'PUBLIC',
    })
    expect(Date.parse(edited.editedAt)).toBeGreaterThanOrEqual(
      Date.parse(edited.createdAt),
    )
    expect(ticket(10).version).toBe(before)
    await failure(
      ticketApi.updateComment(10, comment.id, {
        content: 'Hi',
        visibility: 'INTERNAL',
      }),
      400,
    )
  })
  it('rejects internal Requester creation, other authors and hidden internal edits', async () => {
    await failure(
      ticketApi.addComment(10, { content: 'Private', visibility: 'INTERNAL' }),
      403,
    )
    await failure(
      ticketApi.updateComment(10, 'comment-internal', { content: 'Changed' }),
      404,
    )
    actor = manager
    await failure(
      ticketApi.updateComment(10, 'comment-public', { content: 'Changed' }),
      403,
    )
    await failure(
      ticketApi.addComment(10, { content: ' ', visibility: 'PUBLIC' }),
      400,
    )
    await failure(ticketApi.comments(10, { sort: 'createdAt,desc' }), 400)
  })
  it('allows current technicians to comment but historical technicians only to read', async () => {
    actor = {
      id: 21,
      fullName: 'Kỹ thuật viên',
      status: 'ACTIVE',
      roles: ['TECHNICIAN'],
    }
    const comment = await ticketApi.addComment(12, {
      content: 'Ghi chú xử lý',
      visibility: 'INTERNAL',
    })
    expect((await ticketApi.comments(12, {})).content).toContainEqual(comment)
    actor = manager
    await ticketApi.unassign(12, {
      version: ticket(12).version,
      reason: 'Đổi người xử lý',
    })
    actor = {
      id: 21,
      fullName: 'Kỹ thuật viên',
      status: 'ACTIVE',
      roles: ['TECHNICIAN'],
    }
    expect((await ticketApi.comments(12, {})).content).toHaveLength(1)
    await failure(
      ticketApi.addComment(12, { content: 'Thêm nữa', visibility: 'PUBLIC' }),
      403,
    )
    expect(
      (
        await ticketApi.updateComment(12, comment.id, {
          content: 'Sửa ghi chú của tôi',
        })
      ).content,
    ).toBe('Sửa ghi chú của tôi')
  })
  it('denies new comments and own edits on terminal tickets', async () => {
    await ticketApi.cancel(10, { version: 0, reason: 'Không còn cần hỗ trợ' })
    await failure(
      ticketApi.addComment(10, { content: 'Hello', visibility: 'PUBLIC' }),
      403,
    )
    await failure(
      ticketApi.updateComment(10, 'comment-public', { content: 'Changed' }),
      403,
    )
  })
  it('closes only for reporters with Requester, including mixed roles', async () => {
    await ticketApi.close(15, { version: ticket(15).version })
    expect(ticket(15)).toMatchObject({
      status: 'CLOSED',
      currentAssignment: null,
    })
    expect(ticket(15).closedAt).toBe(ticket(15).updatedAt)
    expect(ticket(15).statusHistory.at(-1).transitionAction).toBe('CLOSE')
    mock.reset()
    actor = manager
    await failure(ticketApi.close(15, { version: ticket(15).version }), 403)
    await failure(
      ticketApi.close(15, {
        version: ticket(15).version,
        reason: 'Xác nhận kết quả',
      }),
      403,
    )
    expect(ticket(15).status).toBe('RESOLVED')
    mock.reset()
    actor = { ...owner, roles: ['REQUESTER', 'ADMIN'] }
    await ticketApi.close(15, { version: ticket(15).version })
    expect(ticket(15).statusHistory.at(-1).transitionAction).toBe('CLOSE')
  })
  it('reopens only RESOLVED with reason, new SLA and consistent history/version', async () => {
    const before = ticket(15).version
    await failure(ticketApi.reopen(15, { version: before, reason: ' ' }), 400)
    await failure(
      ticketApi.reopen(15, { version: before - 1, reason: 'Vẫn lỗi' }),
      409,
    )
    await ticketApi.reopen(15, { version: before, reason: 'Vẫn lỗi' })
    expect(ticket(15)).toMatchObject({
      status: 'REOPENED',
      version: before + 1,
      resolvedAt: null,
      currentAssignment: null,
    })
    expect(Date.parse(ticket(15).slaDueAt)).toBe(
      Date.parse(ticket(15).updatedAt) + mock.store.slaMinutes[15] * 60000,
    )
    expect(ticket(15).statusHistory.at(-1)).toMatchObject({
      fromStatus: 'RESOLVED',
      toStatus: 'REOPENED',
      transitionAction: 'REOPEN',
    })
    await failure(
      ticketApi.reopen(17, { version: ticket(17).version, reason: 'Vẫn lỗi' }),
      422,
    )
    await failure(
      ticketApi.cancel(12, { version: ticket(12).version, reason: 'Hủy' }),
      422,
    )
  })
  it('permits one feedback, validates rating and returns null only for absence', async () => {
    expect(await ticketApi.feedback(17)).toBeNull()
    await failure(ticketApi.addFeedback(17, { rating: 6 }), 400)
    await failure(ticketApi.addFeedback(15, { rating: 5 }), 403)
    const result = await ticketApi.addFeedback(17, {
      rating: 5,
      comment: '  Tốt  ',
    })
    expect(result).toMatchObject({ rating: 5, comment: 'Tốt' })
    expect(await ticketApi.feedback(17)).toEqual(result)
    await failure(ticketApi.addFeedback(17, { rating: 4 }), 409)
    expect(mock.store.feedback).toHaveLength(1)
    actor = manager
    await failure(ticketApi.addFeedback(17, { rating: 5 }), 403)
  })
  it('accepts only one of simultaneous feedback submissions', async () => {
    const results = await Promise.allSettled([
      ticketApi.addFeedback(17, { rating: 4 }),
      ticketApi.addFeedback(17, { rating: 5 }),
    ])
    expect(
      results.filter((result) => result.status === 'fulfilled'),
    ).toHaveLength(1)
    expect(
      results.find((result) => result.status === 'rejected').reason.response
        .status,
    ).toBe(409)
    expect(mock.store.feedback).toHaveLength(1)
  })
  it('returns real PNG bytes and headers through the Authorization client', async () => {
    sessionStorage.setItem('nexora.session.token', 'test-image-token')
    let authorization
    server.events.on('request:start', ({ request }) => {
      authorization = request.headers.get('Authorization')
    })
    const response = await httpClient.get(
      '/tickets/10/attachments/1000/content',
      { responseType: 'arraybuffer' },
    )
    expect(authorization).toBe('Bearer test-image-token')
    expect(response.headers['content-type']).toBe('image/png')
    expect(response.headers['content-disposition']).toContain('filename*=UTF-8')
    expect(Number(response.headers['content-length'])).toBe(
      response.data.byteLength,
    )
    expect(Array.from(new Uint8Array(response.data)).slice(0, 8)).toEqual([
      137, 80, 78, 71, 13, 10, 26, 10,
    ])
    const blob = await ticketApi.attachmentContent(10, 1000)
    expect(blob.type).toBe('image/png')
    expect(blob.size).toBe(response.data.byteLength)
    const blobBytes = await new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(Array.from(new Uint8Array(reader.result)))
      reader.onerror = reject
      reader.readAsArrayBuffer(blob)
    })
    expect(blobBytes).toEqual(Array.from(new Uint8Array(response.data)))
  })
  it('returns safe missing-file and ticket-visibility errors for every subresource', async () => {
    await failure(ticketApi.attachmentContent(10, 999), 404)
    actor = { ...owner, id: 99 }
    await failure(ticketApi.attachmentContent(10, 1000), 404)
    await failure(ticketApi.comments(10, {}), 404)
    await failure(ticketApi.feedback(17), 404)
  })
  it('expires the session on image 401 and leaves failures scoped to their resource', async () => {
    let expired = 0
    const listener = () => {
      expired += 1
    }
    window.addEventListener('nexora:unauthorized', listener)
    try {
      mock.failNextSubresource('attachments', 401)
      await failure(ticketApi.attachmentContent(10, 1000), 401)
      expect(expired).toBe(1)
      mock.failNextSubresource('comments', 503)
      await failure(ticketApi.comments(10, {}), 503)
      expect((await ticketApi.getById(10)).id).toBe(10)
      expect(await ticketApi.feedback(10)).toBeNull()
    } finally {
      window.removeEventListener('nexora:unauthorized', listener)
    }
  })
  it('binds demo reporter identity once and resets all collaboration data', async () => {
    const demo = createTicketWorkflowMock({
      getActor: () => actor,
      adaptReporterId: true,
    })
    server.use(...demo.handlers)
    actor = { ...owner, id: 42, fullName: 'Requester thật' }
    expect((await ticketApi.getById(10)).reporter).toEqual({
      id: 42,
      fullName: 'Requester thật',
    })
    expect((await ticketApi.comments(10, {})).content[0].author.id).toBe(42)
    await failure(ticketApi.getById(18), 404)
    actor = { ...owner, id: 43 }
    await failure(ticketApi.getById(10), 404)
    demo.reset()
    expect((await ticketApi.getById(10)).reporter.id).toBe(43)
  })
})
