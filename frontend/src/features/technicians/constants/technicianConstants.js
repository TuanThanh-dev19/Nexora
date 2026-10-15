export const PROFICIENCY_LEVELS = Object.freeze([
  { value: 1, label: 'Mức 1 - Cơ bản' },
  { value: 2, label: 'Mức 2 - Trung bình' },
  { value: 3, label: 'Mức 3 - Khá' },
  { value: 4, label: 'Mức 4 - Giỏi' },
  { value: 5, label: 'Mức 5 - Chuyên gia' },
])

export const PROFICIENCY_LABELS = Object.freeze({
  1: 'Mức 1 - Cơ bản',
  2: 'Mức 2 - Trung bình',
  3: 'Mức 3 - Khá',
  4: 'Mức 4 - Giỏi',
  5: 'Mức 5 - Chuyên gia',
})

export const AVAILABILITY_FILTER_OPTIONS = Object.freeze([
  { value: '', label: 'Tất cả trạng thái sẵn sàng' },
  { value: 'true', label: 'Sẵn sàng nhận việc' },
  { value: 'false', label: 'Không sẵn sàng' },
])

export const COVERAGE_HELPER_TEXT =
  'Phạm vi phục vụ (coverage) tại một Location sẽ tự động bao gồm tất cả các vùng con (descendants) bên dưới nó.'

export const TECHNICIAN_LIST_HELPER_TEXT =
  'Danh sách phục vụ cấu hình lực lượng kỹ thuật viên. Cấu hình tại đây không tự động đồng nghĩa với việc kỹ thuật viên đủ điều kiện phân công vào từng phiếu yêu cầu.'
