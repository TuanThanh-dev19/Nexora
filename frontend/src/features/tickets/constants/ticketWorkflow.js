import { ROLE_CODES } from '../../../shared/constants/roles'
import { TICKET_STATUS_CODES as STATUS } from './ticketStatuses'

// Presentation subset of the canonical backend TicketWorkflowContract.
export const TICKET_WORKFLOW_ACTIONS = {
  review: {
    label: 'Bắt đầu xem xét',
    statuses: [STATUS.SUBMITTED],
    targetStatus: STATUS.UNDER_REVIEW,
    historyAction: 'BEGIN_REVIEW',
  },
  reject: {
    label: 'Từ chối',
    statuses: [STATUS.SUBMITTED, STATUS.UNDER_REVIEW],
    targetStatus: STATUS.REJECTED,
    historyAction: 'REJECT',
    requiresReason: true,
  },
  cancel: {
    label: 'Hủy phiếu',
    statuses: [
      STATUS.SUBMITTED,
      STATUS.UNDER_REVIEW,
      STATUS.ASSIGNED,
      STATUS.IN_PROGRESS,
      STATUS.REOPENED,
    ],
    targetStatus: STATUS.CANCELLED,
    requiresReason: true,
  },
  assign: {
    label: 'Phân công',
    statuses: [STATUS.UNDER_REVIEW, STATUS.REOPENED],
    targetStatus: STATUS.ASSIGNED,
    historyAction: 'ASSIGN',
  },
  reassign: {
    label: 'Phân công lại',
    statuses: [STATUS.ASSIGNED, STATUS.IN_PROGRESS],
    targetStatus: STATUS.ASSIGNED,
    historyAction: 'REASSIGN',
    requiresReason: true,
  },
  unassign: {
    label: 'Gỡ phân công',
    statuses: [STATUS.ASSIGNED, STATUS.IN_PROGRESS],
    targetStatus: STATUS.UNDER_REVIEW,
    historyAction: 'UNASSIGN',
    requiresReason: true,
  },
}

export const ELIGIBILITY_REASON_LABELS = {
  UNAVAILABLE: 'Không sẵn sàng',
  CAPACITY_REACHED: 'Đã đạt sức chứa',
  SKILL_MISMATCH: 'Không phù hợp kỹ năng',
  COVERAGE_MISMATCH: 'Ngoài khu vực phục vụ',
  USER_INACTIVE: 'Tài khoản không hoạt động',
  MISSING_TECHNICIAN_ROLE: 'Thiếu quyền Technician',
  MISSING_TECHNICIAN_PROFILE: 'Thiếu hồ sơ Technician',
}

export const OVERRIDABLE_ELIGIBILITY_REASONS = [
  'UNAVAILABLE',
  'CAPACITY_REACHED',
  'SKILL_MISMATCH',
  'COVERAGE_MISMATCH',
]

export function canManageTickets(user) {
  return (
    ((user?.status == null || user.status === 'ACTIVE') &&
      user?.roles?.some((role) =>
        [ROLE_CODES.MANAGER, ROLE_CODES.ADMIN].includes(role),
      )) === true
  )
}

export function availableTicketActions(status, user) {
  if (!canManageTickets(user)) return []
  return Object.keys(TICKET_WORKFLOW_ACTIONS).filter((action) =>
    TICKET_WORKFLOW_ACTIONS[action].statuses.includes(status),
  )
}

export function workflowHistoryAction(action, status) {
  if (action !== 'cancel') return TICKET_WORKFLOW_ACTIONS[action].historyAction
  return [STATUS.SUBMITTED, STATUS.UNDER_REVIEW].includes(status)
    ? 'CANCEL_BEFORE_ASSIGNMENT'
    : 'CANCEL_AFTER_ASSIGNMENT'
}
