/**
 * Format error message from backend or mock responses into user-friendly Vietnamese.
 */
export function formatTechnicianError(error) {
  if (!error) return 'Đã có lỗi xảy ra. Vui lòng thử lại.'

  const status = error.response?.status
  const detail = error.response?.data?.detail
  const fieldErrors = error.response?.data?.fieldErrors

  if (fieldErrors && Object.keys(fieldErrors).length > 0) {
    const firstField = Object.keys(fieldErrors)[0]
    return fieldErrors[firstField]
  }

  if (status === 409 || detail?.toLowerCase()?.includes('stale') || detail?.toLowerCase()?.includes('conflict')) {
    return 'Dữ liệu đã bị thay đổi bởi người khác (xung đột phiên bản). Vui lòng tải lại dữ liệu mới nhất.'
  }

  if (detail) {
    if (detail.toLowerCase().includes('not have the technician role')) {
      return 'Người dùng này không có vai trò Kỹ thuật viên (TECHNICIAN).'
    }
    return detail
  }

  switch (status) {
    case 400:
      return 'Dữ liệu cấu hình không hợp lệ. Vui lòng kiểm tra lại.'
    case 401:
      return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.'
    case 403:
      return 'Bạn không có quyền thực hiện thao tác trên hồ sơ này.'
    case 404:
      return 'Không tìm thấy hồ sơ kỹ thuật viên.'
    case 409:
      return 'Dữ liệu đã bị thay đổi bởi người khác (xung đột phiên bản). Vui lòng tải lại dữ liệu mới nhất.'
    default:
      if (!error.response) {
        return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng.'
      }
      return 'Lỗi hệ thống. Vui lòng thử lại sau.'
  }
}

export function isConflictError(error) {
  if (!error) return false
  return (
    error.response?.status === 409 ||
    error.status === 409 ||
    error.response?.data?.detail?.toLowerCase()?.includes('stale') ||
    error.response?.data?.detail?.toLowerCase()?.includes('conflict') ||
    error.message?.toLowerCase()?.includes('conflict') ||
    error.message?.toLowerCase()?.includes('stale')
  )
}
