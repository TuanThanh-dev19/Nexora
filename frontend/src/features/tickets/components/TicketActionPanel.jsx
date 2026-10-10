import { useState } from 'react'
import { Alert, Button, Card, Stack } from 'react-bootstrap'
import { useAuth } from '../../auth/context/useAuth'
import {
  availableTicketActions,
  canManageTickets,
  TICKET_WORKFLOW_ACTIONS,
} from '../constants/ticketWorkflow'
import { useTicketWorkflow } from '../hooks/useTicketWorkflow'
import { TicketActionModal } from './TicketActionModal'
import { TicketAssignmentDialog } from './TicketAssignmentDialog'
import { TicketStatusBadge } from './TicketStatusBadge'

export function TicketActionPanel({ ticket }) {
  const { user } = useAuth()
  const { enabled, mutation, refresh } = useTicketWorkflow()
  const [dialog, setDialog] = useState(null)
  const [notice, setNotice] = useState('')
  if (!canManageTickets(user)) return null
  const actions = availableTicketActions(ticket.status, user)
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
        payload,
      })
      setNotice(
        `${TICKET_WORKFLOW_ACTIONS[dialog.action].label} thành công trong bản mô phỏng.`,
      )
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
          Kỹ thuật viên hiện tại:{' '}
          {ticket.currentAssignment === undefined
            ? 'Dữ liệu phân công chưa được hỗ trợ'
            : (ticket.currentAssignment?.technician.fullName ??
              'Chưa phân công')}
        </p>
        <Alert variant="info">
          {enabled
            ? 'Bản mô phỏng: thay đổi chỉ nằm trong dữ liệu demo, chưa lưu lên máy chủ.'
            : 'Chức năng xử lý phiếu chưa được hỗ trợ. API workflow sẽ được bổ sung ở milestone sau.'}
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
                !enabled ||
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
            Không có thao tác review hoặc phân công cho trạng thái này.
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
