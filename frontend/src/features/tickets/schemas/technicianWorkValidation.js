import {
  RESOLUTION_OUTCOMES,
  WORK_IMAGE_LIMIT,
  WORK_IMAGE_TYPES,
  WORK_LOG_TYPES,
} from '../constants/technicianWork'

export function validVersion(version) {
  return Number.isSafeInteger(version) && version >= 0
}

export function validateWorkLog(value) {
  const errors = {}
  const offsetTime = (time) =>
    typeof time === 'string' &&
    /^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(time) &&
    Number.isFinite(Date.parse(time))
  if (!validVersion(value.version))
    errors.version = 'Phiên bản phiếu không hợp lệ.'
  if (!WORK_LOG_TYPES.some(([type]) => type === value.logType))
    errors.logType = 'Chọn loại nhật ký.'
  if (!value.content?.trim()) errors.content = 'Nhập nội dung công việc.'
  const start = Date.parse(value.startedAt)
  if (!offsetTime(value.startedAt))
    errors.startedAt = 'Nhập thời gian bắt đầu hợp lệ.'
  if (value.endedAt != null && value.endedAt !== '') {
    const end = Date.parse(value.endedAt)
    if (!offsetTime(value.endedAt) || end < start)
      errors.endedAt = 'Thời gian kết thúc phải sau thời gian bắt đầu.'
  }
  if (
    value.minutesSpent != null &&
    (!Number.isInteger(value.minutesSpent) ||
      value.minutesSpent <= 0 ||
      value.minutesSpent > 2147483647)
  )
    errors.minutesSpent = 'Số phút phải là số nguyên dương.'
  return errors
}

export function validateResolution(value, savedLogCount) {
  const errors = {}
  if (!validVersion(value.version))
    errors.version = 'Phiên bản phiếu không hợp lệ.'
  if (
    !RESOLUTION_OUTCOMES.some(
      ([outcome]) => outcome === value.resolutionOutcome,
    )
  )
    errors.resolutionOutcome = 'Chọn kết quả xử lý.'
  if (!value.resolutionSummary?.trim())
    errors.resolutionSummary = 'Nhập tóm tắt kết quả.'
  if (savedLogCount < 1) errors.workLogs = 'Cần ít nhất một nhật ký đã lưu.'
  return errors
}

export function validateWorkImages(files) {
  if (!files.length || files.length > 5) return 'Chọn từ 1 đến 5 ảnh.'
  if (files.some((file) => !WORK_IMAGE_TYPES.includes(file.type)))
    return 'Chỉ chấp nhận ảnh JPEG hoặc PNG.'
  if (files.some((file) => file.size < 1 || file.size > WORK_IMAGE_LIMIT))
    return 'Mỗi ảnh phải có dung lượng từ 1 byte đến 5 MiB.'
  return ''
}
