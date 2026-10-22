import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Card, Form, Modal, Stack } from 'react-bootstrap'
import { isTicketWorkflowMockEnabled } from '../../../shared/config/env'
import { useAuth } from '../../auth/context/useAuth'
import {
  WORK_LOG_TYPES,
  RESOLUTION_OUTCOMES,
  canTechnicianWrite,
} from '../constants/technicianWork'
import { useTechnicianMutation, useWorkLogs } from '../hooks/useTechnicianWork'
import {
  validateResolution,
  validateWorkImages,
  validateWorkLog,
} from '../schemas/technicianWorkValidation'
import { formatTicketTime } from '../utils/ticketFormatting'

function workError(error) {
  const status = error?.response?.status
  if (status === 409) return 'Phiếu đã thay đổi. Tải lại rồi xác nhận lần nữa.'
  if ([403, 404, 422].includes(status))
    return 'Không còn quyền xử lý hoặc phân công đã thay đổi. Vui lòng tải lại.'
  return (
    error?.response?.data?.detail ??
    'Không thể hoàn thành thao tác. Vui lòng thử lại.'
  )
}

function workBlocked(error) {
  return [403, 404, 409, 422].includes(error?.response?.status)
}

function WorkDialog({
  title,
  mutation,
  onClose,
  onReload,
  onSubmit,
  children,
  disabled = false,
}) {
  const blocked = workBlocked(mutation.error)
  return (
    <Modal show onHide={() => !mutation.isPending && onClose()}>
      <Form noValidate onSubmit={onSubmit}>
        <Modal.Header closeButton={!mutation.isPending}>
          <Modal.Title>{title}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {mutation.isError && (
            <Alert variant="danger" role="alert">
              {workError(mutation.error)}
              {blocked && (
                <div className="mt-2">
                  <Button variant="outline-danger" onClick={onReload}>
                    Tải lại phiếu
                  </Button>
                </div>
              )}
            </Alert>
          )}
          <fieldset disabled={mutation.isPending || blocked}>
            {children}
          </fieldset>
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={mutation.isPending}
          >
            Đóng
          </Button>
          <Button
            type="submit"
            disabled={disabled || blocked || mutation.isPending}
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

function WorkLogDialog({ ticket, mutation, submit, close, reload }) {
  const [values, setValues] = useState({
    logType: 'PROGRESS',
    content: '',
    startedAt: '',
    endedAt: '',
    minutesSpent: '',
  })
  const [errors, setErrors] = useState({})
  const send = (event) => {
    event.preventDefault()
    const timestamp = (value) =>
      value && Number.isFinite(Date.parse(value))
        ? new Date(value).toISOString()
        : value
    const payload = {
      version: ticket.version,
      logType: values.logType,
      content: values.content.trim(),
      startedAt: timestamp(values.startedAt),
      endedAt: values.endedAt ? timestamp(values.endedAt) : null,
      minutesSpent:
        values.minutesSpent === '' ? null : Number(values.minutesSpent),
    }
    const next = validateWorkLog(payload)
    setErrors(next)
    if (!Object.keys(next).length) void submit('addWorkLog', payload)
  }
  const field = (name, label, type = 'text') => (
    <Form.Group className="mb-3" controlId={`work-${name}`}>
      <Form.Label>{label}</Form.Label>
      <Form.Control
        type={type}
        value={values[name]}
        isInvalid={Boolean(errors[name])}
        onChange={(event) => {
          setValues({ ...values, [name]: event.target.value })
          setErrors({})
          mutation.reset()
        }}
      />
      {errors[name] && (
        <Form.Control.Feedback type="invalid">
          {errors[name]}
        </Form.Control.Feedback>
      )}
    </Form.Group>
  )
  return (
    <WorkDialog
      title="Thêm nhật ký"
      mutation={mutation}
      onClose={close}
      onReload={reload}
      onSubmit={send}
    >
      <Form.Group className="mb-3" controlId="work-log-type">
        <Form.Label>Loại nhật ký</Form.Label>
        <Form.Select
          value={values.logType}
          onChange={(event) =>
            setValues({ ...values, logType: event.target.value })
          }
        >
          {WORK_LOG_TYPES.map(([code, label]) => (
            <option key={code} value={code}>
              {label}
            </option>
          ))}
        </Form.Select>
      </Form.Group>
      <Form.Group className="mb-3" controlId="work-content">
        <Form.Label>Nội dung</Form.Label>
        <Form.Control
          as="textarea"
          rows={3}
          value={values.content}
          isInvalid={Boolean(errors.content)}
          onChange={(event) => {
            setValues({ ...values, content: event.target.value })
            setErrors({})
            mutation.reset()
          }}
        />
        {errors.content && (
          <Form.Control.Feedback type="invalid">
            {errors.content}
          </Form.Control.Feedback>
        )}
      </Form.Group>
      {field('startedAt', 'Bắt đầu', 'datetime-local')}
      {field('endedAt', 'Kết thúc (không bắt buộc)', 'datetime-local')}
      {field('minutesSpent', 'Số phút (không bắt buộc)', 'number')}
    </WorkDialog>
  )
}

function ResolveDialog({
  ticket,
  savedLogCount,
  mutation,
  submit,
  close,
  reload,
}) {
  const [outcome, setOutcome] = useState('FIXED')
  const [summary, setSummary] = useState('')
  const [errors, setErrors] = useState({})
  const send = (event) => {
    event.preventDefault()
    const payload = {
      version: ticket.version,
      resolutionOutcome: outcome,
      resolutionSummary: summary.trim(),
    }
    const next = validateResolution(payload, savedLogCount)
    setErrors(next)
    if (!Object.keys(next).length) void submit('resolve', payload)
  }
  return (
    <WorkDialog
      title="Hoàn tất xử lý"
      mutation={mutation}
      onClose={close}
      onReload={reload}
      onSubmit={send}
    >
      <p>{ticket.ticketNumber} · Xác nhận kết quả xử lý?</p>
      {savedLogCount < 1 && (
        <Alert variant="warning">
          Cần ít nhất một nhật ký đã lưu trước khi hoàn tất.
        </Alert>
      )}
      <Form.Group className="mb-3" controlId="resolution-outcome">
        <Form.Label>Kết quả</Form.Label>
        <Form.Select
          value={outcome}
          isInvalid={Boolean(errors.resolutionOutcome)}
          onChange={(event) => setOutcome(event.target.value)}
        >
          {RESOLUTION_OUTCOMES.map(([code, label]) => (
            <option key={code} value={code}>
              {label}
            </option>
          ))}
        </Form.Select>
      </Form.Group>
      <Form.Group controlId="resolution-summary">
        <Form.Label>Tóm tắt kết quả</Form.Label>
        <Form.Control
          as="textarea"
          rows={3}
          value={summary}
          isInvalid={Boolean(errors.resolutionSummary)}
          onChange={(event) => {
            setSummary(event.target.value)
            setErrors({})
            mutation.reset()
          }}
        />
        {errors.resolutionSummary && (
          <Form.Control.Feedback type="invalid">
            {errors.resolutionSummary}
          </Form.Control.Feedback>
        )}
      </Form.Group>
    </WorkDialog>
  )
}

function WorkImages({ mutation, upload }) {
  const [previews, setPreviews] = useState([])
  const [error, setError] = useState('')
  const blocked = workBlocked(mutation.error)
  const urls = useRef(new Set())
  useEffect(() => {
    const activeUrls = urls.current
    return () => activeUrls.forEach((url) => URL.revokeObjectURL(url))
  }, [])
  const files = previews.map(({ file }) => file)
  const choose = (event) => {
    const added = Array.from(event.target.files).map((file) => {
      const url = URL.createObjectURL(file)
      urls.current.add(url)
      return { file, url }
    })
    setPreviews([...previews, ...added])
    setError(validateWorkImages([...files, ...added.map(({ file }) => file)]))
    event.target.value = ''
  }
  const send = async () => {
    if (blocked) return
    const next = validateWorkImages(files)
    setError(next)
    if (next) return
    if (await upload(files)) {
      previews.forEach(({ url }) => {
        URL.revokeObjectURL(url)
        urls.current.delete(url)
      })
      setPreviews([])
    }
  }
  return (
    <Card className="ticket-section mb-3">
      <Card.Body>
        <h2 className="h5">Ảnh kết quả</h2>
        <p className="small text-secondary">
          JPEG/PNG, tối đa 5 ảnh, mỗi ảnh tối đa 5 MiB. Dữ liệu demo chỉ nằm
          trong bản mô phỏng.
        </p>
        <Form.Group controlId="work-images">
          <Form.Label>Chọn ảnh</Form.Label>
          <Form.Control
            type="file"
            accept="image/jpeg,image/png"
            multiple
            onChange={choose}
            disabled={mutation.isPending || blocked}
          />
        </Form.Group>
        {error && (
          <Alert variant="danger" className="mt-2">
            {error}
          </Alert>
        )}
        {previews.length > 0 && (
          <div className="work-previews mt-3">
            {previews.map(({ file, url }, index) => (
              <div key={`${file.name}-${index}`}>
                <img src={url} alt={`Xem trước ${file.name}`} />
                <p className="small text-break mb-1">{file.name}</p>
                <Button
                  size="sm"
                  variant="outline-secondary"
                  onClick={() => {
                    URL.revokeObjectURL(url)
                    urls.current.delete(url)
                    setPreviews(previews.filter((_, i) => i !== index))
                    setError('')
                  }}
                  disabled={mutation.isPending}
                >
                  Bỏ ảnh
                </Button>
              </div>
            ))}
          </div>
        )}
        <Button
          className="mt-3"
          onClick={send}
          disabled={
            mutation.isPending || blocked || !files.length || Boolean(error)
          }
        >
          {mutation.isPending ? 'Đang tải ảnh...' : 'Tải ảnh mô phỏng'}
        </Button>
      </Card.Body>
    </Card>
  )
}

export function TechnicianWorkPanel({ ticket }) {
  const { user } = useAuth()
  const { mutation, refresh } = useTechnicianMutation(ticket)
  const logs = useWorkLogs(ticket.id)
  const [dialog, setDialog] = useState(null)
  const [notice, setNotice] = useState('')
  if (!user?.roles?.includes('TECHNICIAN')) return null
  const available = true
  const canStart = canTechnicianWrite(ticket, user, 'ASSIGNED')
  const canWork = canTechnicianWrite(ticket, user)
  const submit = async (action, payload) => {
    try {
      await mutation.mutateAsync({ action, payload })
      setDialog(null)
      await refresh()
      await logs.refetch()
      setNotice(
        action === 'start'
          ? 'Bắt đầu xử lý phiếu thành công.'
          : action === 'addWorkLog'
            ? 'Thêm nhật ký công việc thành công.'
            : action === 'resolve'
              ? 'Hoàn tất xử lý phiếu thành công.'
              : 'Thao tác thành công.',
      )
    } catch {
      /* Feedback stays visible in the dialog. */
    }
  }
  const upload = async (files) => {
    try {
      await mutation.mutateAsync({ action: 'uploadWorkResult', files })
      setNotice('Ảnh đã tải trong bản mô phỏng; chưa lưu trên máy chủ.')
      return true
    } catch {
      return false
    }
  }
  const reload = async () => {
    setDialog(null)
    mutation.reset()
    await refresh()
  }
  const open = (name) => {
    mutation.reset()
    setNotice('')
    setDialog({ name, ticket })
  }
  return (
    <>
      <Card className="ticket-section mb-3">
        <Card.Body>
          <h2 className="h5">Công việc kỹ thuật viên</h2>
          <p>
            Phân công hiện tại:{' '}
            {ticket.currentAssignment === undefined
              ? 'Dữ liệu phân công chưa khả dụng'
              : ticket.currentAssignment
                ? `${ticket.currentAssignment.technician.fullName} · ${formatTicketTime(ticket.currentAssignment.assignedAt)}`
                : 'Không còn phân công hiện tại'}
          </p>
          {notice && (
            <Alert variant="success" role="status">
              {notice}
            </Alert>
          )}
          {mutation.isError && !dialog && (
            <Alert variant="danger" role="alert">
              {workError(mutation.error)}{' '}
              <Button size="sm" variant="outline-danger" onClick={reload}>
                Tải lại phiếu
              </Button>
            </Alert>
          )}
          <Stack direction="horizontal" gap={2} className="flex-wrap">
            {ticket.status === 'ASSIGNED' && (
              <Button
                disabled={!canStart || mutation.isPending}
                onClick={() => open('start')}
              >
                Bắt đầu xử lý
              </Button>
            )}
            {ticket.status === 'IN_PROGRESS' && (
              <>
                <Button
                  disabled={!canWork || mutation.isPending}
                  onClick={() => open('log')}
                >
                  Thêm nhật ký
                </Button>
                <Button
                  disabled={
                    !canWork || mutation.isPending || !logs.data?.length
                  }
                  onClick={() => open('resolve')}
                >
                  Hoàn tất xử lý
                </Button>
              </>
            )}
          </Stack>
          {canWork &&
            !logs.data?.length &&
            !logs.isPending &&
            !logs.isError && (
              <p className="small text-secondary mt-2 mb-0">
                Cần ít nhất một nhật ký đã lưu để hoàn tất xử lý.
              </p>
            )}
        </Card.Body>
      </Card>
      <Card className="ticket-section mb-3">
        <Card.Body>
          <h2 className="h5">Nhật ký công việc</h2>
          {logs.isError ? (
            <Alert variant="danger">
              Không thể tải nhật ký.{' '}
              <Button size="sm" onClick={() => logs.refetch()}>
                Thử lại
              </Button>
            </Alert>
          ) : logs.isPending ? (
            <p>Đang tải nhật ký...</p>
          ) : !logs.data?.length ? (
            <p className="text-secondary mb-0">Chưa có nhật ký.</p>
          ) : (
            <ol className="ticket-timeline">
              {logs.data.map((log) => (
                <li key={log.id}>
                  <strong>
                    {WORK_LOG_TYPES.find(
                      ([code]) => code === log.logType,
                    )?.[1] ?? log.logType}
                  </strong>
                  <p className="mb-1 text-break">{log.content}</p>
                  <small>
                    {formatTicketTime(log.startedAt)} →{' '}
                    {formatTicketTime(log.endedAt)} ·{' '}
                    {log.minutesSpent ?? '—'} phút
                  </small>
                </li>
              ))}
            </ol>
          )}
        </Card.Body>
      </Card>
      {/* Ẩn control upload WORK_RESULT theo phạm vi demo: {canWork && <WorkImages mutation={mutation} upload={upload} />} */}
      {dialog?.name === 'start' && (
        <WorkDialog
          title="Bắt đầu xử lý"
          mutation={mutation}
          onClose={() => setDialog(null)}
          onReload={reload}
          onSubmit={(event) => {
            event.preventDefault()
            void submit('start', { version: dialog.ticket.version })
          }}
        >
          <p>Xác nhận bắt đầu xử lý {dialog.ticket.ticketNumber}?</p>
        </WorkDialog>
      )}
      {dialog?.name === 'log' && (
        <WorkLogDialog
          ticket={dialog.ticket}
          mutation={mutation}
          submit={submit}
          close={() => setDialog(null)}
          reload={reload}
        />
      )}
      {dialog?.name === 'resolve' && (
        <ResolveDialog
          ticket={dialog.ticket}
          savedLogCount={logs.data?.length ?? 0}
          mutation={mutation}
          submit={submit}
          close={() => setDialog(null)}
          reload={reload}
        />
      )}
    </>
  )
}
