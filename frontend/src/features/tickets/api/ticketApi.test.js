import { describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { ticketApi } from './ticketApi'
vi.mock('../../../shared/api/httpClient', () => ({
  httpClient: { get: vi.fn(), post: vi.fn() },
}))

describe('ticketApi', () => {
  it('sends English enum params and AbortSignal and returns the page unchanged', async () => {
    const data = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    }
    httpClient.get.mockResolvedValueOnce({ data })
    const search =
      'page=0&size=20&sort=createdAt,desc&status=SUBMITTED&priority=HIGH&locationId=1'
    const signal = new AbortController().signal
    expect(await ticketApi.search(search, signal)).toBe(data)
    expect(httpClient.get).toHaveBeenCalledWith('/tickets', {
      params: new URLSearchParams(search),
      signal,
    })
  })
  it('preserves duplicate, unknown, empty params and large IDs for backend validation', async () => {
    httpClient.get.mockResolvedValueOnce({ data: {} })
    const search =
      'page=0&page=1&keyword=unsupported&priority=&locationId=9223372036854775807'
    await ticketApi.search(search)
    const { params } = httpClient.get.mock.lastCall[1]
    expect([...params]).toEqual([...new URLSearchParams(search)])
    expect(params.getAll('page')).toEqual(['0', '1'])
    expect(params.get('locationId')).toBe('9223372036854775807')
  })
  it('reads detail with cancellation and does not wrap the response', async () => {
    const data = { id: 12, version: 3 }
    httpClient.get.mockResolvedValueOnce({ data })
    const signal = new AbortController().signal
    expect(await ticketApi.getById('12', signal)).toBe(data)
    expect(httpClient.get).toHaveBeenCalledWith('/tickets/12', { signal })
  })
  it.each(['review', 'reject', 'cancel', 'assign', 'reassign', 'unassign'])(
    'sends the expected version to the explicit %s endpoint',
    async (action) => {
      const payload = {
        version: 0,
        reason: 'Lý do',
        technicianId: 21,
        overrideReason: 'Được duyệt',
      }
      const data = { id: 12, status: 'ASSIGNED', version: 1 }
      httpClient.post.mockResolvedValueOnce({ data })
      expect(await ticketApi[action]('12', payload)).toBe(data)
      expect(httpClient.post).toHaveBeenLastCalledWith(
        `/tickets/12/${action}`,
        payload,
      )
    },
  )
  it('uses the eligibility endpoint with cancellation and agreed query parameters', async () => {
    const data = { content: [], totalElements: 0 }
    const params = { page: 0, size: 20, query: 'An', eligibility: 'ELIGIBLE' }
    const signal = new AbortController().signal
    httpClient.get.mockResolvedValueOnce({ data })
    expect(await ticketApi.eligibleTechnicians('12', params, signal)).toBe(data)
    expect(httpClient.get).toHaveBeenLastCalledWith(
      '/tickets/12/eligible-technicians',
      { params, signal },
    )
  })
  it('propagates workflow failures without a mock success fallback', async () => {
    const error = { response: { status: 404 } }
    httpClient.post.mockRejectedValueOnce(error)
    await expect(ticketApi.review('12', { version: 0 })).rejects.toBe(error)
  })
})
