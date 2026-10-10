import {
  OVERRIDABLE_ELIGIBILITY_REASONS,
  TICKET_WORKFLOW_ACTIONS,
} from '../constants/ticketWorkflow'

export const REASON_MAX_LENGTH = 500
export const OVERRIDE_REASON_MAX_LENGTH = 1000

export function candidateEligibility(candidate) {
  if (!candidate) return { blocked: true, requiresOverride: false }
  const reasons = candidate.eligibilityReasons ?? []
  const blocked =
    candidate.userStatus !== 'ACTIVE' ||
    !candidate.hasTechnicianRole ||
    !candidate.hasProfile ||
    reasons.some(
      (reason) =>
        !reason.overridable ||
        !OVERRIDABLE_ELIGIBILITY_REASONS.includes(reason.code),
    )
  return { blocked, requiresOverride: !blocked && reasons.length > 0 }
}

export function validateWorkflowPayload(action, payload, candidate) {
  const errors = {}
  if (!Number.isSafeInteger(payload.version) || payload.version < 0)
    errors.version = 'Phiên bản phiếu không hợp lệ. Vui lòng tải lại.'
  if (TICKET_WORKFLOW_ACTIONS[action]?.requiresReason) {
    if (typeof payload.reason !== 'string' || !payload.reason.trim())
      errors.reason = 'Vui lòng nhập lý do.'
    else if (payload.reason.length > REASON_MAX_LENGTH)
      errors.reason = `Lý do tối đa ${REASON_MAX_LENGTH} ký tự.`
  }
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
      if (
        eligibility.requiresOverride &&
        (typeof payload.overrideReason !== 'string' ||
          !payload.overrideReason.trim())
      )
        errors.overrideReason = 'Vui lòng nhập lý do ghi đè điều kiện.'
    }
    if (
      payload.overrideReason != null &&
      (typeof payload.overrideReason !== 'string' ||
        payload.overrideReason.length > OVERRIDE_REASON_MAX_LENGTH)
    )
      errors.overrideReason = `Lý do ghi đè tối đa ${OVERRIDE_REASON_MAX_LENGTH} ký tự.`
  }
  return errors
}
