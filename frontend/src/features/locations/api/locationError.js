export function formatLocationError(error) {
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
      return 'Mã địa điểm này đã tồn tại trong hệ thống.'
    }
    if (detail.includes('cycle in the location hierarchy')) {
      return 'Không thể chọn địa điểm cha vì sẽ tạo vòng lặp trong phân cấp.'
    }
    if (detail.includes('must be a root location')) {
      return 'Cơ sở (CAMPUS) phải là địa điểm gốc và không được có địa điểm cha.'
    }
    if (detail.includes('requires a parent location')) {
      return 'Địa điểm này bắt buộc phải chọn địa điểm cha phù hợp.'
    }
    if (detail.includes('must have a parent of type')) {
      return detail
    }
    return detail
  }

  switch (status) {
    case 400:
      return 'Dữ liệu không hợp lệ. Vui lòng kiểm tra lại các trường.'
    case 401:
      return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.'
    case 403:
      return 'Bạn không có quyền thực hiện thao tác quản trị địa điểm.'
    case 404:
      return 'Không tìm thấy địa điểm yêu cầu.'
    case 409:
    case 422:
      return 'Thao tác bị từ chối do vi phạm quy tắc phân cấp hoặc dữ liệu liên quan.'
    default:
      if (!error.response) {
        return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng.'
      }
      return 'Lỗi hệ thống. Vui lòng thử lại sau.'
  }
}
