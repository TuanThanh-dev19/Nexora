export const LOCATION_TYPE_CODES = Object.freeze({
  CAMPUS: 'CAMPUS',
  BUILDING: 'BUILDING',
  FLOOR: 'FLOOR',
  ROOM: 'ROOM',
  AREA: 'AREA',
})

export const LOCATION_TYPES = Object.freeze(Object.values(LOCATION_TYPE_CODES))

export const LOCATION_TYPE_LABELS = Object.freeze({
  [LOCATION_TYPE_CODES.CAMPUS]: 'Cơ sở',
  [LOCATION_TYPE_CODES.BUILDING]: 'Tòa nhà',
  [LOCATION_TYPE_CODES.FLOOR]: 'Tầng',
  [LOCATION_TYPE_CODES.ROOM]: 'Phòng',
  [LOCATION_TYPE_CODES.AREA]: 'Khu vực',
})

export const VALID_PARENT_TYPE = Object.freeze({
  [LOCATION_TYPE_CODES.BUILDING]: LOCATION_TYPE_CODES.CAMPUS,
  [LOCATION_TYPE_CODES.FLOOR]: LOCATION_TYPE_CODES.BUILDING,
  [LOCATION_TYPE_CODES.ROOM]: LOCATION_TYPE_CODES.FLOOR,
  [LOCATION_TYPE_CODES.AREA]: LOCATION_TYPE_CODES.FLOOR,
})

export const ROOT_LOCATION_TYPES = Object.freeze([LOCATION_TYPE_CODES.CAMPUS])

export function getExpectedParentType(childType) {
  return VALID_PARENT_TYPE[childType] ?? null
}

export function isValidParentChildType(childType, parentType) {
  if (ROOT_LOCATION_TYPES.includes(childType)) {
    return parentType == null
  }
  return VALID_PARENT_TYPE[childType] === parentType
}
