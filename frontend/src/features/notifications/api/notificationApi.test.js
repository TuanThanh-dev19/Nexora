import { beforeEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import {
  getNotificationsApi,
  getUnreadCountApi,
  markNotificationAsReadApi,
} from './notificationApi'

vi.mock('../../../shared/api/httpClient', () => ({
  httpClient: {
    get: vi.fn(),
    patch: vi.fn(),
  },
}))

describe('notificationApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getNotificationsApi calls GET /api/v1/notifications with default parameters', async () => {
    const mockData = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    }
    httpClient.get.mockResolvedValueOnce({ data: mockData })

    const result = await getNotificationsApi()

    expect(httpClient.get).toHaveBeenCalledWith('/api/v1/notifications', {
      params: { page: 0, size: 20, unreadOnly: false },
    })
    expect(result).toEqual(mockData)
  })

  it('getNotificationsApi calls GET /api/v1/notifications with custom parameters', async () => {
    const mockData = {
      content: [{ id: '60f1b2b3c9e77b0015f8a001' }],
      page: 2,
      size: 10,
      totalElements: 25,
      totalPages: 3,
    }
    httpClient.get.mockResolvedValueOnce({ data: mockData })

    const result = await getNotificationsApi({ page: 2, size: 10, unreadOnly: true })

    expect(httpClient.get).toHaveBeenCalledWith('/api/v1/notifications', {
      params: { page: 2, size: 10, unreadOnly: true },
    })
    expect(result).toEqual(mockData)
  })

  it('getUnreadCountApi calls GET /api/v1/notifications/unread-count', async () => {
    const mockData = { unreadCount: 5 }
    httpClient.get.mockResolvedValueOnce({ data: mockData })

    const result = await getUnreadCountApi()

    expect(httpClient.get).toHaveBeenCalledWith('/api/v1/notifications/unread-count')
    expect(result).toEqual(mockData)
  })

  it('markNotificationAsReadApi calls PATCH /api/v1/notifications/:id/read', async () => {
    const notificationId = '60f1b2b3c9e77b0015f8a002'
    const mockData = {
      id: notificationId,
      read: true,
      readAt: '2026-10-30T10:00:00Z',
    }
    httpClient.patch.mockResolvedValueOnce({ data: mockData })

    const result = await markNotificationAsReadApi(notificationId)

    expect(httpClient.patch).toHaveBeenCalledWith(
      `/api/v1/notifications/${notificationId}/read`,
    )
    expect(result).toEqual(mockData)
  })
})

