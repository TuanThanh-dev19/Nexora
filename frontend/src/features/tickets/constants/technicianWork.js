export const WORK_LOG_TYPES = [
  ['DIAGNOSIS', 'Chẩn đoán'],
  ['PROGRESS', 'Tiến độ'],
  ['REPAIR', 'Sửa chữa'],
  ['RESOLUTION', 'Kết quả'],
  ['NO_FAULT_FOUND', 'Không phát hiện lỗi'],
]
export const RESOLUTION_OUTCOMES = [
  ['FIXED', 'Đã sửa'],
  ['TEMPORARY_FIX', 'Khắc phục tạm thời'],
  ['NO_FAULT_FOUND', 'Không phát hiện lỗi'],
  ['UNRESOLVED', 'Chưa khắc phục'],
]
export const WORK_IMAGE_TYPES = ['image/jpeg', 'image/png']
export const WORK_IMAGE_LIMIT = 5 * 1024 * 1024

export function isCurrentTechnician(ticket, user) {
  const assignment = ticket?.currentAssignment
  return (
    (user?.status == null || user.status === 'ACTIVE') &&
    user?.roles?.includes('TECHNICIAN') === true &&
    assignment?.endedAt === null &&
    String(assignment.technician?.technicianId) === String(user.id)
  )
}

export function canTechnicianWrite(ticket, user, status = 'IN_PROGRESS') {
  return (
    isCurrentTechnician(ticket, user) &&
    ticket.status === status &&
    Number.isSafeInteger(ticket.version) &&
    ticket.version >= 0
  )
}

export function slaState(ticket, now = Date.now()) {
  if (!['ASSIGNED', 'IN_PROGRESS'].includes(ticket.status) || !ticket.slaDueAt)
    return null
  const due = Date.parse(ticket.slaDueAt)
  if (Number.isNaN(due)) return null
  return due < now
    ? 'BREACHED'
    : due - now <= 2 * 60 * 60 * 1000
      ? 'AT_RISK'
      : 'ON_TIME'
}
