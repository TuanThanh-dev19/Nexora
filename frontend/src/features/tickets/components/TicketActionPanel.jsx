import { useState } from 'react'
import { Alert, Button, Card, Stack } from 'react-bootstrap'
import { useAuth } from '../../auth/context/useAuth'
import { isTicketWorkflowMockEnabled } from '../../../shared/config/env'
import { demoScope } from '../../../shared/config/demoScope'
import {
  availableTicketActions,
  canManageTickets,
  getResolverTechnician,
  isTicketOwner,
  TICKET_WORKFLOW_ACTIONS,
} from '../constants/ticketWorkflow'
import { useTicketWorkflow } from '../hooks/useTicketWorkflow'
import { TicketActionModal } from './TicketActionModal'
import { TicketAssignmentDialog } from './TicketAssignmentDialog'
import { TicketStatusBadge } from './TicketStatusBadge'

export function TicketActionPanel({ ticket }) {
  const { user } = useAuth()
  const { enabled, isActionEnabled, mutation, refresh } = useTicketWorkflow()
  const [dialog, setDialog] = useState(null)
  const [notice, setNotice] = useState('')
  if (!canManageTickets(user) && !isTicketOwner(ticket, user)) return null
  const actions = availableTicketActions(ticket, user).filter(
    (action) => demoScope.actions[action] === true,
  )
  const open = (action) => {
    mutation.reset()
    setNotice('')
    // Keep the expected version and context seen at confirmation, even if a read refreshes.
    setDialog({ action, ticket })
  }
  const submit = async (payload) => {
    try {
      await mutation.mutateAsync({
        action: dialog.action,
        ticketId: dialog.ticket.id,
        ticket: dialog.ticket,
        payload,
      })
      setNotice(`${TICKET_WORKFLOW_ACTIONS[dialog.action].label} thành công.`)
      setDialog(null)
    } catch {
      // Mutation feedback stays in the dialog, preserving its inputs.
    }
  }
  const reload = async () => {
    setDialog(null)
    mutation.reset()
    await refresh()
  }
  const Dialog = ['assign', 'reassign'].includes(dialog?.action)
    ? TicketAssignmentDialog
    : TicketActionModal
  return (
    <Card className="ticket-section mb-4">
      <Card.Body>
        <h2 className="h5">Xử lý phiếu</h2>
        <Stack direction="horizontal" gap={2} className="flex-wrap mb-2">
          <span>Trạng thái hiện tại:</span>
          <TicketStatusBadge status={ticket.status} />
        </Stack>
        <p>
          {['RESOLVED', 'CLOSED'].includes(ticket.status)
            ? 'Kỹ thuật viên xử lý: '
            : 'Kỹ thuật viên hiện tại: '}
          {ticket.currentAssignment === undefined
            ? 'Dữ liệu phân công chưa được hỗ trợ'
            : (ticket.currentAssignment?.technician.fullName ??
              (['RESOLVED', 'CLOSED'].includes(ticket.status)
                ? getResolverTechnician(ticket)
                  ? `${getResolverTechnician(ticket)} (Đã hoàn thành)`
                  : 'Đã hoàn thành xử lý'
                : 'Chưa phân công'))}
        </p>
        <Alert variant="info">
          {isTicketWorkflowMockEnabled()
            ? 'Bản mô phỏng: thay đổi chỉ nằm trong dữ liệu demo, chưa lưu lên máy chủ.'
            : 'Tiếp nhận, phân công và đóng phiếu được lưu trên máy chủ.'}
        </Alert>
        {notice && (
          <Alert variant="success" role="status">
            {notice}
          </Alert>
        )}
        <Stack direction="horizontal" gap={2} className="flex-wrap">
          {actions.map((action) => (
            <Button
              key={action}
              variant={
                ['reject', 'cancel'].includes(action)
                  ? 'outline-danger'
                  : 'outline-primary'
              }
              disabled={
                !isActionEnabled(action) ||
                mutation.isPending ||
                !Number.isSafeInteger(ticket.version) ||
                ticket.version < 0
              }
              onClick={() => open(action)}
            >
              {TICKET_WORKFLOW_ACTIONS[action].label}
            </Button>
          ))}
        </Stack>
        {!actions.length && (
          <p className="text-secondary mb-0">
            Không có thao tác xử lý cho trạng thái này.
          </p>
        )}
      </Card.Body>
      {enabled && dialog && (
        <Dialog
          key={dialog.action}
          action={dialog.action}
          ticket={dialog.ticket}
          mutation={mutation}
          onHide={() => setDialog(null)}
          onSubmit={submit}
          onReload={reload}
        />
      )}
    </Card>
  )
}
