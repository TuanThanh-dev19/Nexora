export function formatEquipmentTypeError(error) {
  if (!error) return 'Đã có lỗi xảy ra. Vui lòng thử lại.'

  const status = error.response?.status
  const detail = error.response?.data?.detail
  const fieldErrors = error.response?.data?.fieldErrors

  if (fieldErrors && Object.keys(fieldErrors).length > 0) {
    const firstKey = Object.keys(fieldErrors)[0]
    return fieldErrors[firstKey]
  }

  if (detail) {
    if (detail.includes('already exists')) {
      return 'Mã loại thiết bị này đã tồn tại trong hệ thống.'
    }
    return detail
  }

  switch (status) {
    case 400:
      return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại các trường.'
    case 401:
      return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.'
    case 403:
      return 'Bạn không có quyền thực hiện thao tác quản trị loại thiết bị.'
    case 404:
      return 'Không tìm thấy loại thiết bị yêu cầu.'
    case 409:
    case 422:
      return 'Thao tác bị từ chối do vi phạm quy tắc hệ thống hoặc ràng buộc dữ liệu.'
    default:
      if (!error.response) {
        return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng.'
      }
      return 'Lỗi hệ thống. Vui lòng thử lại sau.'
  }
}
