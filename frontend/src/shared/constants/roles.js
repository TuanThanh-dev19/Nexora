export const ROLE_CODES = Object.freeze({
  REQUESTER: 'REQUESTER',
  TECHNICIAN: 'TECHNICIAN',
  MANAGER: 'MANAGER',
  ADMIN: 'ADMIN',
})

export const ROLES = Object.freeze(Object.values(ROLE_CODES))

export const ROLE_LABELS = Object.freeze({
  [ROLE_CODES.REQUESTER]: 'Requester',
  [ROLE_CODES.TECHNICIAN]: 'Technician',
  [ROLE_CODES.MANAGER]: 'Manager',
  [ROLE_CODES.ADMIN]: 'Admin',
})

export const ROLE_OPTIONS = Object.freeze(
  ROLES.map((value) => Object.freeze({ value, label: ROLE_LABELS[value] })),
)
