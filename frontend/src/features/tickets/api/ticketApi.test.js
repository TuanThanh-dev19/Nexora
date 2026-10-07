import { describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { ticketApi } from './ticketApi'
vi.mock('../../../shared/api/httpClient', () => ({
  httpClient: { get: vi.fn() },
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
    const params = {
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      status: 'SUBMITTED',
      priority: 'HIGH',
      locationId: '1',
    }
    const signal = new AbortController().signal
    expect(await ticketApi.search(params, signal)).toBe(data)
    expect(httpClient.get).toHaveBeenCalledWith('/tickets', { params, signal })
  })
  it('reads detail with cancellation and does not wrap the response', async () => {
    const data = { id: 12, version: 3 }
    httpClient.get.mockResolvedValueOnce({ data })
    const signal = new AbortController().signal
    expect(await ticketApi.getById('12', signal)).toBe(data)
    expect(httpClient.get).toHaveBeenCalledWith('/tickets/12', { signal })
  })
})
