import { TICKET_WORKFLOW_ACTIONS } from '../constants/ticketWorkflow'

export const REASON_MAX_LENGTH = 500
export const REASSIGN_REASON_MAX_LENGTH = 1000
export const OVERRIDE_REASON_MAX_LENGTH = 1000

export function candidateEligibility(candidate) {
  if (!candidate) return { blocked: true, requiresOverride: false }
  const reasons = candidate.eligibilityReasons ?? []
  const blocked =
    candidate.userStatus !== 'ACTIVE' ||
    !candidate.hasTechnicianRole ||
    !candidate.hasProfile ||
    reasons.length > 0
  return { blocked, requiresOverride: false }
}

export function validateWorkflowPayload(
  action,
  payload,
  candidate,
  requiresReason = TICKET_WORKFLOW_ACTIONS[action]?.requiresReason,
) {
  const errors = {}
  const reasonLimit =
    action === 'reassign' ? REASSIGN_REASON_MAX_LENGTH : REASON_MAX_LENGTH
  if (!Number.isSafeInteger(payload.version) || payload.version < 0)
    errors.version = 'Phiên bản phiếu không hợp lệ. Vui lòng tải lại.'
  if (requiresReason) {
    if (typeof payload.reason !== 'string' || !payload.reason.trim())
      errors.reason = 'Vui lòng nhập lý do.'
    else if (payload.reason.trim().length > reasonLimit)
      errors.reason = `Lý do tối đa ${reasonLimit} ký tự.`
  }
  if (
    action === 'close' &&
    payload.reason != null &&
    (typeof payload.reason !== 'string' ||
      payload.reason.length > REASON_MAX_LENGTH)
  )
    errors.reason = `Lý do tối đa ${REASON_MAX_LENGTH} ký tự.`
  if (['assign', 'reassign'].includes(action)) {
    if (
      !candidate ||
      String(candidate.technicianId) !== String(payload.technicianId)
    )
      errors.technicianId = 'Vui lòng chọn kỹ thuật viên.'
    else {
      const eligibility = candidateEligibility(candidate)
      if (eligibility.blocked)
        errors.technicianId = 'Không thể phân công kỹ thuật viên này.'
    }
  }
  return errors
}
