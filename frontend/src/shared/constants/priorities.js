export const PRIORITY_CODES = Object.freeze({
  LOW: 'LOW',
  NORMAL: 'NORMAL',
  HIGH: 'HIGH',
  URGENT: 'URGENT',
})

export const PRIORITIES = Object.freeze(Object.values(PRIORITY_CODES))

export const PRIORITY_LABELS = Object.freeze({
  LOW: 'Thấp',
  NORMAL: 'Bình thường',
  HIGH: 'Cao',
  URGENT: 'Khẩn cấp',
})
export const PRIORITY_OPTIONS = Object.freeze(
  PRIORITIES.map((value) => ({ value, label: PRIORITY_LABELS[value] })),
)
