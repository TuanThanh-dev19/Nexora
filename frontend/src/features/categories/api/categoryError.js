const messages = {
  'The published form requires equipment. Publish a compatible form first.':
    'Biểu mẫu đang phát hành bắt buộc tài sản. Phát hành biểu mẫu phù hợp trước khi đổi chính sách.',
  'Skill not found.': 'Không tìm thấy kỹ năng.',
  'SLA policy not found.': 'Không tìm thấy chính sách SLA.',
  'Stored validation rules are not valid JSON.':
    'Quy tắc đã lưu không phải JSON hợp lệ.',
  'Use a valid regular expression (at most 500 characters).':
    'Nhập biểu thức chính quy hợp lệ (tối đa 500 ký tự).',
  'Choose at least one image MIME type.':
    'Chọn ít nhất một định dạng MIME ảnh.',
  'Supported types: image/jpeg, image/png, image/webp, image/gif.':
    'Định dạng hỗ trợ: image/jpeg, image/png, image/webp, image/gif.',
  'Use a finite number; step must be positive.':
    'Nhập số hữu hạn; bước số phải lớn hơn 0.',
  'Use a non-negative integer (file limits must be positive).':
    'Nhập số nguyên không âm (giới hạn tệp phải lớn hơn 0).',
  'Maximum must be greater than or equal to minimum.':
    'Giá trị tối đa phải lớn hơn hoặc bằng giá trị tối thiểu.',
  'Field keys must be unique (case insensitive).':
    'Mã trường không được trùng (không phân biệt hoa thường).',
  'Only select fields may have options.':
    'Chỉ trường chọn một/chọn nhiều được có lựa chọn.',
  'Select fields need at least one active option.':
    'Trường chọn cần ít nhất một lựa chọn hoạt động.',
  'Option values must be unique (case insensitive).':
    'Giá trị lựa chọn không được trùng (không phân biệt hoa thường).',
  'Activate the category before publishing.':
    'Kích hoạt danh mục trước khi phát hành.',
  'Equipment cannot be required when assets are forbidden.':
    'Không thể bắt buộc tài sản khi danh mục cấm tài sản.',
  'Choose an active default skill.': 'Chọn kỹ năng mặc định đang hoạt động.',
  'Choose an active SLA policy.': 'Chọn chính sách SLA đang hoạt động.',
  'Category code already exists.': 'Mã danh mục đã tồn tại.',
  'Category code is immutable.': 'Không thể thay đổi mã danh mục đã tạo.',
  'Validation rules must be a JSON object.':
    'Quy tắc kiểm tra phải là một đối tượng JSON.',
  'must not be blank': 'Không được để trống.',
  'must be greater than or equal to 0': 'Phải lớn hơn hoặc bằng 0.',
}
export const localizeCategoryMessage = (message) => {
  if (messages[message]) return messages[message]
  if (/^Rule is not supported for [A-Z_]+\.$/.test(message))
    return 'Quy tắc không được hỗ trợ cho kiểu trường này.'
  if (/^Use an ISO (DATE|DATETIME) value\.$/.test(message))
    return 'Nhập ngày/giờ theo định dạng ISO.'
  return String(message)
}

export const fieldErrorLabel = (path) => {
  const labels = {
    form: 'Biểu mẫu',
    category: 'Danh mục',
    fields: 'Các trường',
    code: 'Mã',
    name: 'Tên',
    description: 'Mô tả',
    assetPolicy: 'Chính sách tài sản',
    defaultPriority: 'Mức ưu tiên',
    active: 'Hoạt động',
    requiresEquipment: 'Bắt buộc tài sản',
    minAttachments: 'Số ảnh tối thiểu',
    defaultSkillId: 'Kỹ năng mặc định',
    defaultSlaPolicyId: 'Chính sách SLA mặc định',
    revision: 'Phiên bản chỉnh sửa',
    fieldKey: 'Mã trường',
    label: 'Nhãn',
    fieldType: 'Kiểu trường',
    helpText: 'Hướng dẫn',
    placeholder: 'Gợi ý nhập',
    validationRules: 'Quy tắc kiểm tra',
    options: 'Lựa chọn',
    optionValue: 'Giá trị',
    optionLabel: 'Nhãn lựa chọn',
  }
  return path
    .replace(/fields\[(\d+)\]/g, (_, n) => `Trường ${Number(n) + 1}`)
    .replace(/options\[(\d+)\]/g, (_, n) => `Lựa chọn ${Number(n) + 1}`)
    .split('.')
    .map((part) => labels[part] ?? part)
    .join(' · ')
}
export const localizeFieldErrors = (errors) =>
  Object.fromEntries(
    Object.entries(errors).map(([key, value]) => [
      key,
      localizeCategoryMessage(value),
    ]),
  )
export const errorMessage = (error) => {
  const status = error?.response?.status
  return (
    {
      401: 'Phiên đăng nhập đã hết hạn.',
      403: 'Bạn không có quyền quản trị danh mục.',
      404: 'Không tìm thấy danh mục hoặc biểu mẫu.',
      409: 'Phiên bản đã thay đổi. Kiểm tra và tải bản mới trước khi tiếp tục.',
    }[status] ??
    (error?.response?.data?.detail
      ? `Không thể hoàn tất thao tác: ${localizeCategoryMessage(error.response.data.detail)}`
      : 'Không kết nối được máy chủ. Vui lòng thử lại.')
  )
}
