export const USER_STATUS_CODES = Object.freeze({
  ACTIVE: 'ACTIVE',
  LOCKED: 'LOCKED',
  DISABLED: 'DISABLED',
})

export const USER_STATUSES = Object.freeze(Object.values(USER_STATUS_CODES))

export const USER_STATUS_LABELS = Object.freeze({
  [USER_STATUS_CODES.ACTIVE]: 'Hoạt động',
  [USER_STATUS_CODES.LOCKED]: 'Tạm khóa',
  [USER_STATUS_CODES.DISABLED]: 'Vô hiệu hóa',
})
