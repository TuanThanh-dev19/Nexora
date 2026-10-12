import { useState } from 'react'
import { Alert, Button, Form, Modal } from 'react-bootstrap'
import { TICKET_WORKFLOW_ACTIONS } from '../constants/ticketWorkflow'
import {
  REASON_MAX_LENGTH,
  validateWorkflowPayload,
} from '../schemas/ticketWorkflowValidation'

function workflowErrorMessage(error) {
  const status = error?.response?.status
  if (status === 403 || status === 404)
    return 'Không thể thực hiện thao tác này. Vui lòng kiểm tra quyền và tải lại phiếu.'
  if (status === 409)
    return 'Phiếu đã thay đổi. Tải lại dữ liệu và xác nhận thao tác một lần nữa.'
  if (status == null) return 'Không thể kết nối máy chủ. Vui lòng thử lại.'
  return (
    error.response.data?.detail ??
    'Không thể thực hiện thao tác. Vui lòng thử lại.'
  )
}

// The two dialogs share confirmation, pending and conflict behavior.
export function TicketWorkflowModal({
  title,
  mutation,
  onHide,
  onSubmit,
  onReload,
  disabled,
  size,
  children,
}) {
  const conflict = mutation.error?.response?.status === 409
  return (
    <Modal
      show
      size={size}
      onHide={() => !mutation.isPending && onHide()}
      backdrop={mutation.isPending ? 'static' : true}
      keyboard={!mutation.isPending}
    >
      <Form noValidate onSubmit={onSubmit}>
        <Modal.Header closeButton={!mutation.isPending}>
          <Modal.Title>{title}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {mutation.isError && (
            <Alert variant={conflict ? 'warning' : 'danger'} role="alert">
              <p className="mb-2">{workflowErrorMessage(mutation.error)}</p>
              {conflict && (
                <Button variant="outline-dark" onClick={onReload}>
                  Tải lại phiếu
                </Button>
              )}
            </Alert>
          )}
          <fieldset disabled={mutation.isPending || conflict}>
            {children}
          </fieldset>
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="secondary"
            disabled={mutation.isPending}
            onClick={onHide}
          >
            Đóng
          </Button>
          <Button
            type="submit"
            disabled={disabled || mutation.isPending || conflict}
          >
            {mutation.isPending
              ? 'Đang xử lý…'
              : `Xác nhận ${title.toLowerCase()}`}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  )
}

export function TicketReasonField({
  id,
  label = 'Lý do',
  value,
  onChange,
  error,
  maxLength = REASON_MAX_LENGTH,
}) {
  return (
    <Form.Group className="mb-3" controlId={id}>
      <Form.Label>
        {label} <span aria-hidden="true">*</span>
      </Form.Label>
      <Form.Control
        as="textarea"
        rows={3}
        value={value}
        required
        maxLength={maxLength}
        onChange={(event) => onChange(event.target.value)}
        isInvalid={Boolean(error)}
        aria-invalid={Boolean(error)}
        aria-describedby={`${id}-help ${id}-error`}
      />
      <Form.Text id={`${id}-help`}>
        Bắt buộc, tối đa {maxLength} ký tự.
      </Form.Text>
      <Form.Control.Feedback type="invalid" id={`${id}-error`}>
        {error}
      </Form.Control.Feedback>
    </Form.Group>
  )
}

export function TicketActionModal({
  action,
  ticket,
  mutation,
  onHide,
  onSubmit,
  onReload,
}) {
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState({})
  const definition = TICKET_WORKFLOW_ACTIONS[action]
  const fieldErrors = {
    ...mutation.error?.response?.data?.fieldErrors,
    ...errors,
  }
  const submit = (event) => {
    event.preventDefault()
    if (mutation.isPending || mutation.error?.response?.status === 409) return
    const payload = { version: ticket.version }
    if (definition.requiresReason) payload.reason = reason.trim()
    const next = validateWorkflowPayload(action, payload)
    setErrors(next)
    if (!Object.keys(next).length) void onSubmit(payload)
  }
  return (
    <TicketWorkflowModal
      title={definition.label}
      mutation={mutation}
      onHide={onHide}
      onSubmit={submit}
      onReload={onReload}
    >
      <p>
        <strong>{ticket.ticketNumber}</strong> · {ticket.title}
      </p>
      {ticket.currentAssignment && (
        <p>
          Kỹ thuật viên hiện tại: {ticket.currentAssignment.technician.fullName}
        </p>
      )}
      <p>Xác nhận {definition.label.toLowerCase()} phiếu này?</p>
      {fieldErrors.version && (
        <Alert variant="danger">{fieldErrors.version}</Alert>
      )}
      {definition.requiresReason && (
        <TicketReasonField
          id="ticket-action-reason"
          value={reason}
          error={fieldErrors.reason}
          onChange={(value) => {
            setReason(value)
            setErrors({})
            mutation.reset()
          }}
        />
      )}
    </TicketWorkflowModal>
  )
}
