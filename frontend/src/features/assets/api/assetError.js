import { isApiError } from '../../../shared/api/apiError'

export function isConflictError(error) {
  return error?.response?.status === 409
}

export function isForbiddenError(error) {
  return error?.response?.status === 403
}

export function isNotFoundError(error) {
  return error?.response?.status === 404
}

export function formatAssetError(
  error,
  defaultMessage = 'Đã có lỗi xảy ra. Vui lòng thử lại.',
) {
  if (!error) return defaultMessage

  if (error.response?.status === 403) {
    return 'Bạn không có quyền thực hiện thao tác này.'
  }

  if (error.response?.status === 404) {
    return error.response?.data?.detail ?? 'Không tìm thấy tài sản.'
  }

  if (error.response?.status === 409) {
    return (
      error.response?.data?.detail ??
      'Dữ liệu đã bị thay đổi bởi thao tác khác. Vui lòng tải lại và thử lại.'
    )
  }

  const data = error.response?.data
  if (isApiError(data)) {
    return data.detail || data.title || defaultMessage
  }

  if (typeof error.message === 'string' && error.message.trim()) {
    return error.message
  }

  return defaultMessage
}

export function extractFieldErrors(error) {
  const data = error?.response?.data
  if (data?.fieldErrors && typeof data.fieldErrors === 'object') {
    return data.fieldErrors
  }
  return {}
}
