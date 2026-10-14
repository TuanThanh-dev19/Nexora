import { canManageTickets, isTicketOwner } from './ticketWorkflow'
import { isCurrentTechnician } from './technicianWork'

export function isNonterminalTicket(ticket) {
  return [
    'SUBMITTED',
    'UNDER_REVIEW',
    'ASSIGNED',
    'IN_PROGRESS',
    'REOPENED',
    'RESOLVED',
  ].includes(ticket?.status)
}

export function canReadInternalComments(user) {
  return (
    (user?.status == null || user.status === 'ACTIVE') &&
    user?.roles?.some((role) =>
      ['TECHNICIAN', 'MANAGER', 'ADMIN'].includes(role),
    ) === true
  )
}

export function canCreateComment(ticket, user, visibility = 'PUBLIC') {
  if (
    !isNonterminalTicket(ticket) ||
    !['PUBLIC', 'INTERNAL'].includes(visibility)
  )
    return false
  if (canManageTickets(user) || isCurrentTechnician(ticket, user)) return true
  return visibility === 'PUBLIC' && isTicketOwner(ticket, user)
}

export function canEditComment(ticket, user, comment) {
  return (
    user?.id != null &&
    (user.status == null || user.status === 'ACTIVE') &&
    isNonterminalTicket(ticket) &&
    String(comment.author.id) === String(user.id) &&
    (comment.visibility === 'PUBLIC' || canReadInternalComments(user))
  )
}

export function canSubmitFeedback(ticket, user) {
  return ticket?.status === 'CLOSED' && isTicketOwner(ticket, user)
}

export function collaborationErrorMessage(error) {
  const status = error?.response?.status
  if (status === 403 || status === 404)
    return 'Không thể truy cập dữ liệu này. Vui lòng kiểm tra quyền và thử lại.'
  if (status === 409)
    return 'Dữ liệu đã thay đổi. Vui lòng tải lại trước khi tiếp tục.'
  return (
    error?.response?.data?.detail ??
    error?.message ??
    'Không thể tải dữ liệu. Vui lòng thử lại.'
  )
}
