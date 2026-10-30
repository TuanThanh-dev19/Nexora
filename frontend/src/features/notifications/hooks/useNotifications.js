import { useCallback, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../auth/context/useAuth'
import {
  getNotificationsApi,
  getUnreadCountApi,
  markNotificationAsReadApi,
} from '../api/notificationApi'

export const NOTIFICATION_POLL_INTERVAL_MS = 30_000

export function useNotifications() {
  const { user, isAuthenticated } = useAuth()
  const queryClient = useQueryClient()

  const [isOpen, setIsOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [size] = useState(20)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [actionError, setActionError] = useState(null)

  const userId = user?.id

  // 1. Polling unread count: strictly tied to active authentication and current user
  const unreadCountQuery = useQuery({
    queryKey: ['notifications', 'unread-count', userId],
    queryFn: getUnreadCountApi,
    enabled: Boolean(isAuthenticated && userId),
    refetchInterval: NOTIFICATION_POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    staleTime: 10_000,
  })

  // 2. Fetch notifications list: enabled when user is authenticated and dropdown is open
  const notificationsQuery = useQuery({
    queryKey: ['notifications', 'list', userId, page, size, unreadOnly],
    queryFn: () => getNotificationsApi({ page, size, unreadOnly }),
    enabled: Boolean(isAuthenticated && userId && isOpen),
    staleTime: 5_000,
  })

  // 3. Mark as read mutation
  const markReadMutation = useMutation({
    mutationFn: (notificationId) => markNotificationAsReadApi(notificationId),
    onSuccess: (updatedNotification) => {
      setActionError(null)

      // Optimistically or deterministically update unread count cache
      queryClient.setQueryData(
        ['notifications', 'unread-count', userId],
        (old) => {
          if (!old) return old
          return {
            ...old,
            unreadCount: Math.max(0, (old.unreadCount ?? 0) - 1),
          }
        },
      )

      // Update notification list cache for current user
      queryClient.setQueriesData(
        { queryKey: ['notifications', 'list', userId] },
        (oldPage) => {
          if (!oldPage || !oldPage.content) return oldPage
          return {
            ...oldPage,
            content: oldPage.content.map((item) =>
              item.id === updatedNotification.id
                ? {
                    ...item,
                    read: true,
                    readAt: updatedNotification.readAt ?? item.readAt ?? new Date().toISOString(),
                  }
                : item,
            ),
          }
        },
      )

      // Refetch queries in background to guarantee absolute synchronization
      void queryClient.invalidateQueries({
        queryKey: ['notifications', 'unread-count', userId],
      })
    },
    onError: (error) => {
      setActionError(
        error?.response?.data?.detail ??
          error?.message ??
          'Không thể đánh dấu thông báo là đã đọc.',
      )
    },
  })

  const markAsRead = useCallback(
    async (notificationId) => {
      setActionError(null)
      return await markReadMutation.mutateAsync(notificationId)
    },
    [markReadMutation],
  )

  const toggleDropdown = useCallback((nextOpen) => {
    setIsOpen((prev) => (typeof nextOpen === 'boolean' ? nextOpen : !prev))
    setActionError(null)
  }, [])

  const handleFilterChange = useCallback((onlyUnread) => {
    setUnreadOnly(onlyUnread)
    setPage(0)
    setActionError(null)
  }, [])

  const nextPage = useCallback(() => {
    if (notificationsQuery.data && page + 1 < notificationsQuery.data.totalPages) {
      setPage((prev) => prev + 1)
    }
  }, [notificationsQuery.data, page])

  const prevPage = useCallback(() => {
    setPage((prev) => Math.max(0, prev - 1))
  }, [])

  const refresh = useCallback(() => {
    setActionError(null)
    void unreadCountQuery.refetch()
    void notificationsQuery.refetch()
  }, [unreadCountQuery, notificationsQuery])

  return {
    isOpen,
    toggleDropdown,
    unreadCount: unreadCountQuery.data?.unreadCount ?? 0,
    isUnreadCountLoading: unreadCountQuery.isLoading,
    notifications: notificationsQuery.data?.content ?? [],
    pagination: {
      page: notificationsQuery.data?.page ?? page,
      size: notificationsQuery.data?.size ?? size,
      totalElements: notificationsQuery.data?.totalElements ?? 0,
      totalPages: notificationsQuery.data?.totalPages ?? 0,
    },
    isLoadingList: notificationsQuery.isLoading,
    isFetchingList: notificationsQuery.isFetching,
    listError: notificationsQuery.error,
    actionError,
    unreadOnly,
    setUnreadOnly: handleFilterChange,
    nextPage,
    prevPage,
    markAsRead,
    isMarkingRead: markReadMutation.isPending,
    refresh,
  }
}
