import { httpClient } from '../../../shared/api/httpClient'
import { isAdminUserMockEnabled } from '../../../shared/config/env'
import { adminUserMockAdapter } from '../mocks/adminUserMock'

/**
 * Format error message from backend or mock responses into user-friendly Vietnamese.
 */
export function formatAdminUserError(error) {
  if (!error) return 'Đã có lỗi xảy ra. Vui lòng thử lại.'

  const status = error.response?.status
  const detail = error.response?.data?.detail
  const fieldErrors = error.response?.data?.fieldErrors

  if (fieldErrors && Object.keys(fieldErrors).length > 0) {
    const firstField = Object.keys(fieldErrors)[0]
    return fieldErrors[firstField]
  }

  if (detail) {
    if (detail.toLowerCase().includes('already in use') || detail.toLowerCase().includes('already exists')) {
      return 'Email này đã được sử dụng trong hệ thống.'
    }
    if (detail.toLowerCase().includes('concurrent') || detail.toLowerCase().includes('conflict')) {
      return 'Dữ liệu người dùng đã bị thay đổi bởi thao tác khác. Vui lòng tải lại trang và thử lại.'
    }
    return detail
  }

  switch (status) {
    case 400:
      return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại.'
    case 401:
      return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.'
    case 403:
      return 'Bạn không có quyền thực hiện thao tác quản trị này.'
    case 404:
      return 'Không tìm thấy người dùng trong hệ thống.'
    case 409:
    case 422:
      return 'Email này đã tồn tại hoặc vi phạm quy tắc hệ thống.'
    default:
      if (!error.response) {
        return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng.'
      }
      return 'Lỗi hệ thống. Vui lòng thử lại sau.'
  }
}

export const adminUserApi = {
  /**
   * Danh sách người dùng phân trang và lọc theo role / status.
   * @param {Object} params - { page, size, sort, status, role }
   */
  async list(params = {}) {
    if (isAdminUserMockEnabled()) {
      return adminUserMockAdapter.listUsers(params)
    }
    const { data } = await httpClient.get('/admin/users', { params })
    return data
  },

  /**
   * Chi tiết người dùng theo ID.
   * @param {number|string} userId
   */
  async getById(userId) {
    if (isAdminUserMockEnabled()) {
      return adminUserMockAdapter.getUserById(userId)
    }
    const { data } = await httpClient.get(`/admin/users/${userId}`)
    return data
  },

  /**
   * Tạo tài khoản người dùng mới.
   * Lưu ý: confirmPassword là trường client-side, KHÔNG được gửi lên backend.
   * @param {{ email: string, fullName: string, password: string, roles: string[], status?: string }} payload
   */
  async create(payload) {
    // Sanitize payload: strip any confirmPassword and trim values
    const safePayload = {
      email: payload.email.trim(),
      fullName: payload.fullName.trim(),
      password: payload.password,
      roles: payload.roles,
      ...(payload.status ? { status: payload.status } : {}),
    }

    if (isAdminUserMockEnabled()) {
      return adminUserMockAdapter.createUser(safePayload)
    }
    const { data } = await httpClient.post('/admin/users', safePayload)
    return data
  },

  /**
   * Cập nhật trạng thái người dùng (ACTIVE, LOCKED, DISABLED).
   * @param {number|string} userId
   * @param {{ status: string, version?: number }} payload
   */
  async updateStatus(userId, payload) {
    if (isAdminUserMockEnabled()) {
      return adminUserMockAdapter.updateUserStatus(userId, payload)
    }
    const { data } = await httpClient.patch(`/admin/users/${userId}/status`, payload)
    return data
  },

  /**
   * Cập nhật vai trò (roles) của người dùng.
   * @param {number|string} userId
   * @param {{ roles: string[] }} payload
   */
  async updateRoles(userId, payload) {
    if (isAdminUserMockEnabled()) {
      return adminUserMockAdapter.updateUserRoles(userId, payload)
    }
    const { data } = await httpClient.put(`/admin/users/${userId}/roles`, payload)
    return data
  },
}
