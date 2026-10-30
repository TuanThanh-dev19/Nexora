import { httpClient } from '../../../shared/api/httpClient'

/**
 * Lấy danh sách thông báo của người dùng hiện tại có phân trang và lọc unread.
 */
export async function getNotificationsApi({ page = 0, size = 20, unreadOnly = false } = {}) {
  const response = await httpClient.get('/api/v1/notifications', {
    params: { page, size, unreadOnly },
  })
  return response.data
}

/**
 * Lấy số lượng thông báo chưa đọc của người dùng hiện tại.
 */
export async function getUnreadCountApi() {
  const response = await httpClient.get('/api/v1/notifications/unread-count')
  return response.data
}

/**
 * Đánh dấu một thông báo là đã đọc.
 */
export async function markNotificationAsReadApi(notificationId) {
  const response = await httpClient.patch(`/api/v1/notifications/${notificationId}/read`)
  return response.data
}

