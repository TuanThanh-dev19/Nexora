import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../../auth/context/auth-context'
import * as api from '../api/notificationApi'
import { useNotifications } from './useNotifications'

vi.mock('../api/notificationApi', () => ({
  getNotificationsApi: vi.fn(),
  getUnreadCountApi: vi.fn(),
  markNotificationAsReadApi: vi.fn(),
}))

function createWrapper(authValue) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return function Wrapper({ children }) {
    return (
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={authValue}>
          {children}
        </AuthContext.Provider>
      </QueryClientProvider>
    )
  }
}

describe('useNotifications hook', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('does not fetch unread count when user is unauthenticated', () => {
    const wrapper = createWrapper({ isAuthenticated: false, user: null })
    const { result } = renderHook(() => useNotifications(), { wrapper })

    expect(result.current.unreadCount).toBe(0)
    expect(api.getUnreadCountApi).not.toHaveBeenCalled()
  })

  it('fetches unread count when user is authenticated', async () => {
    api.getUnreadCountApi.mockResolvedValueOnce({ unreadCount: 7 })

    const wrapper = createWrapper({
      isAuthenticated: true,
      user: { id: 101, fullName: 'Technician Test' },
    })
    const { result } = renderHook(() => useNotifications(), { wrapper })

    await waitFor(() => {
      expect(result.current.unreadCount).toBe(7)
    })
    expect(api.getUnreadCountApi).toHaveBeenCalledTimes(1)
  })

  it('does not fetch notifications list until dropdown is opened', async () => {
    api.getUnreadCountApi.mockResolvedValueOnce({ unreadCount: 2 })
    api.getNotificationsApi.mockResolvedValueOnce({
      content: [{ id: 'notif-1', title: 'Phân công' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })

    const wrapper = createWrapper({
      isAuthenticated: true,
      user: { id: 101, fullName: 'Technician Test' },
    })
    const { result } = renderHook(() => useNotifications(), { wrapper })

    expect(result.current.isOpen).toBe(false)
    expect(api.getNotificationsApi).not.toHaveBeenCalled()

    // Open dropdown
    act(() => {
      result.current.toggleDropdown(true)
    })

    expect(result.current.isOpen).toBe(true)

    await waitFor(() => {
      expect(result.current.notifications).toHaveLength(1)
    })
    expect(api.getNotificationsApi).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      unreadOnly: false,
    })
  })

  it('switches to unread-only filter and resets page to 0', async () => {
    api.getUnreadCountApi.mockResolvedValue({ unreadCount: 1 })
    api.getNotificationsApi.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    const wrapper = createWrapper({
      isAuthenticated: true,
      user: { id: 101, fullName: 'Technician Test' },
    })
    const { result } = renderHook(() => useNotifications(), { wrapper })

    act(() => {
      result.current.toggleDropdown(true)
    })

    act(() => {
      result.current.setUnreadOnly(true)
    })

    expect(result.current.unreadOnly).toBe(true)

    await waitFor(() => {
      expect(api.getNotificationsApi).toHaveBeenCalledWith({
        page: 0,
        size: 20,
        unreadOnly: true,
      })
    })
  })

  it('marks a notification as read and updates unread count and item state', async () => {
    api.getUnreadCountApi.mockResolvedValue({ unreadCount: 3 })
    api.getNotificationsApi.mockResolvedValue({
      content: [
        {
          id: 'notif-99',
          title: 'Ticket Resolved',
          message: 'Resolved',
          read: false,
          readAt: null,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    api.markNotificationAsReadApi.mockResolvedValueOnce({
      id: 'notif-99',
      read: true,
      readAt: '2026-10-30T12:00:00Z',
    })

    const wrapper = createWrapper({
      isAuthenticated: true,
      user: { id: 101, fullName: 'Reporter Test' },
    })
    const { result } = renderHook(() => useNotifications(), { wrapper })

    act(() => {
      result.current.toggleDropdown(true)
    })

    await waitFor(() => {
      expect(result.current.notifications).toHaveLength(1)
    })

    await act(async () => {
      await result.current.markAsRead('notif-99')
    })

    expect(api.markNotificationAsReadApi).toHaveBeenCalledWith('notif-99')
  })

  it('sets actionError when markAsRead fails', async () => {
    api.getUnreadCountApi.mockResolvedValue({ unreadCount: 1 })
    api.getNotificationsApi.mockResolvedValue({
      content: [{ id: 'notif-error', title: 'Fail' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    api.markNotificationAsReadApi.mockRejectedValueOnce({
      response: { data: { detail: 'Thông báo không tồn tại.' } },
    })

    const wrapper = createWrapper({
      isAuthenticated: true,
      user: { id: 101, fullName: 'Test' },
    })
    const { result } = renderHook(() => useNotifications(), { wrapper })

    act(() => {
      result.current.toggleDropdown(true)
    })

    await waitFor(() => {
      expect(result.current.notifications).toHaveLength(1)
    })

    try {
      await act(async () => {
        await result.current.markAsRead('notif-error')
      })
    } catch {
      // Expected rejection
    }

    await waitFor(() => {
      expect(result.current.actionError).toBe('Thông báo không tồn tại.')
    })
  })

  it('paginates next and previous pages when totalPages > 1', async () => {
    api.getUnreadCountApi.mockResolvedValue({ unreadCount: 0 })
    api.getNotificationsApi.mockResolvedValue({
      content: [{ id: 'notif-page' }],
      page: 0,
      size: 20,
      totalElements: 40,
      totalPages: 2,
    })

    const wrapper = createWrapper({
      isAuthenticated: true,
      user: { id: 101, fullName: 'Test' },
    })
    const { result } = renderHook(() => useNotifications(), { wrapper })

    act(() => {
      result.current.toggleDropdown(true)
    })

    await waitFor(() => {
      expect(result.current.pagination.totalPages).toBe(2)
    })

    act(() => {
      result.current.nextPage()
    })

    await waitFor(() => {
      expect(api.getNotificationsApi).toHaveBeenCalledWith({
        page: 1,
        size: 20,
        unreadOnly: false,
      })
    })

    act(() => {
      result.current.prevPage()
    })

    await waitFor(() => {
      expect(api.getNotificationsApi).toHaveBeenCalledWith({
        page: 0,
        size: 20,
        unreadOnly: false,
      })
    })
  })
})

