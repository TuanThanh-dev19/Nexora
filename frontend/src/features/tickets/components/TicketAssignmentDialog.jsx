import { useEffect, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Col,
  Form,
  Row,
  Stack,
  Table,
} from 'react-bootstrap'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { useDebounce } from '../../../shared/hooks/useDebounce'
import {
  ELIGIBILITY_REASON_LABELS,
  TICKET_WORKFLOW_ACTIONS,
} from '../constants/ticketWorkflow'
import { useEligibleTechnicians } from '../hooks/useTicketWorkflow'
import {
  candidateEligibility,
  validateWorkflowPayload,
} from '../schemas/ticketWorkflowValidation'
import { TicketReasonField, TicketWorkflowModal } from './TicketActionModal'

export function TicketAssignmentDialog({
  action,
  ticket,
  mutation,
  onHide,
  onSubmit,
  onReload,
}) {
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('ALL')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [technicianId, setTechnicianId] = useState(null)
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState({})
  const queryText = useDebounce(search.trim())
  const candidates = useEligibleTechnicians(
    ticket.id,
    {
      page,
      size,
      query: queryText || undefined,
      eligibility: filter === 'ALL' ? undefined : filter,
    },
    true,
  )
  const selectedTechnicianId =
    mutation.error?.response?.status === 422 ? null : technicianId
  const selected = candidates.data?.content.find(
    (candidate) => candidate.technicianId === selectedTechnicianId,
  )
  const eligibility = candidateEligibility(selected)
  const loading = candidates.isFetching || search.trim() !== queryText
  const fieldErrors = {
    ...mutation.error?.response?.data?.fieldErrors,
    ...(mutation.error?.response?.status === 422 ? {} : errors),
  }

  // Reload candidates on 422 eligibility conflict
  const refetchCandidates = candidates.refetch
  useEffect(() => {
    if (mutation.error?.response?.status === 422) {
      void refetchCandidates()
    }
  }, [mutation.error, refetchCandidates])

  const clearSelection = () => {
    setTechnicianId(null)
    setErrors({})
    mutation.reset()
  }

  const submit = (event) => {
    event.preventDefault()
    if (
      loading ||
      candidates.isError ||
      mutation.isPending ||
      mutation.error?.response?.status === 409
    )
      return
    const payload = { version: ticket.version, technicianId: selectedTechnicianId }
    if (action === 'reassign') payload.reason = reason.trim()
    const next = validateWorkflowPayload(action, payload, selected)
    setErrors(next)
    if (!Object.keys(next).length) void onSubmit(payload)
  }

  return (
    <TicketWorkflowModal
      size="xl"
      title={TICKET_WORKFLOW_ACTIONS[action].label}
      mutation={mutation}
      onHide={onHide}
      onSubmit={submit}
      onReload={onReload}
      disabled={
        !selected || eligibility.blocked || loading || candidates.isError
      }
    >
      <p>
        <strong>{ticket.ticketNumber}</strong> · {ticket.title}
      </p>
      <p>
        Kỹ thuật viên hiện tại:{' '}
        {ticket.currentAssignment?.technician.fullName ?? 'Chưa phân công'}
      </p>
      <Row className="g-3 mb-3">
        <Col md={6}>
          <Form.Group controlId="candidate-search">
            <Form.Label>Tìm kỹ thuật viên</Form.Label>
            <Form.Control
              type="search"
              value={search}
              placeholder="Tên hoặc email"
              onChange={(event) => {
                setSearch(event.target.value)
                setPage(0)
                clearSelection()
              }}
            />
          </Form.Group>
        </Col>
        <Col md={4}>
          <Form.Group controlId="candidate-eligibility">
            <Form.Label>Điều kiện phân công</Form.Label>
            <Form.Select
              value={filter}
              onChange={(event) => {
                setFilter(event.target.value)
                setPage(0)
                clearSelection()
              }}
            >
              <option value="ALL">Tất cả ứng viên</option>
              <option value="ELIGIBLE">Đủ điều kiện</option>
              <option value="BLOCKED">Không thể phân công</option>
            </Form.Select>
          </Form.Group>
        </Col>
        <Col md={2}>
          <Form.Group controlId="candidate-size">
            <Form.Label>Mỗi trang</Form.Label>
            <Form.Select
              value={size}
              onChange={(event) => {
                setSize(Number(event.target.value))
                setPage(0)
                clearSelection()
              }}
            >
              {[5, 20, 50].map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
        </Col>
      </Row>
      {loading && <LoadingSpinner label="Đang tải ứng viên..." />}
      {candidates.isError ? (
        <Alert variant="danger" role="alert">
          Không thể tải ứng viên. Chi tiết phiếu vẫn có thể xem.
          <Button
            className="ms-2"
            variant="outline-danger"
            onClick={() => candidates.refetch()}
          >
            Thử lại
          </Button>
        </Alert>
      ) : (
        candidates.data && (
          <>
            <Table responsive hover className="align-middle ticket-table">
              <thead>
                <tr>
                  {[
                    'Chọn',
                    'Kỹ thuật viên',
                    'Sẵn sàng',
                    'Khối lượng / sức chứa',
                    'Kỹ năng',
                    'Khu vực phục vụ',
                    'Điều kiện',
                  ].map((label) => (
                    <th key={label}>{label}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {candidates.data.content.map((candidate) => {
                  const state = candidateEligibility(candidate)
                  return (
                    <tr key={candidate.technicianId}>
                      <td>
                        <Form.Check
                          type="radio"
                          name="candidate"
                          aria-label={`Chọn ${candidate.fullName}`}
                          checked={candidate.technicianId === selectedTechnicianId}
                          disabled={state.blocked || loading}
                          onChange={() => {
                            clearSelection()
                            setTechnicianId(candidate.technicianId)
                          }}
                        />
                      </td>
                      <td>
                        <strong>{candidate.fullName}</strong>
                        <div className="small text-secondary">
                          {candidate.email}
                        </div>
                      </td>
                      <td>
                        {candidate.available == null
                          ? '—'
                          : candidate.available
                            ? 'Có'
                            : 'Không'}
                      </td>
                      <td>
                        {candidate.activeTicketCount} /{' '}
                        {candidate.maxActiveTickets ?? '—'}
                      </td>
                      <td>
                        {candidate.skills
                          .map((skill) => skill.name)
                          .join(', ') || '—'}
                      </td>
                      <td>
                        {candidate.serviceAreas
                          .map((area) => area.name)
                          .join(', ') || '—'}
                      </td>
                      <td>
                        <Badge bg={state.blocked ? 'danger' : 'success'}>
                          {state.blocked
                            ? 'Không thể phân công'
                            : 'Đủ điều kiện'}
                        </Badge>
                        {candidate.eligibilityReasons &&
                          candidate.eligibilityReasons.length > 0 && (
                            <ul className="small mb-0 mt-1">
                              {candidate.eligibilityReasons.map(({ code }) => (
                                <li key={code}>
                                  {ELIGIBILITY_REASON_LABELS[code] ?? code}
                                </li>
                              ))}
                            </ul>
                          )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </Table>
            {candidates.data.content.length === 0 && (
              <p>Không có ứng viên phù hợp.</p>
            )}
            <Stack
              direction="horizontal"
              gap={2}
              className="justify-content-between flex-wrap mb-3"
              aria-label="Phân trang ứng viên"
            >
              <span>
                {candidates.data.totalElements} ứng viên · Trang{' '}
                {candidates.data.page + 1}/
                {Math.max(1, candidates.data.totalPages)}
              </span>
              <Stack direction="horizontal" gap={2}>
                <Button
                  variant="outline-secondary"
                  disabled={loading || page === 0}
                  onClick={() => {
                    setPage(page - 1)
                    clearSelection()
                  }}
                >
                  Trang trước
                </Button>
                <Button
                  variant="outline-secondary"
                  disabled={loading || page + 1 >= candidates.data.totalPages}
                  onClick={() => {
                    setPage(page + 1)
                    clearSelection()
                  }}
                >
                  Trang sau
                </Button>
              </Stack>
            </Stack>
          </>
        )
      )}
      {fieldErrors.version && (
        <Alert variant="danger">{fieldErrors.version}</Alert>
      )}
      {fieldErrors.technicianId && (
        <Alert variant="danger">{fieldErrors.technicianId}</Alert>
      )}
      {selected && (
        <p role="status">
          Đã chọn: <strong>{selected.fullName}</strong>
        </p>
      )}
      {action === 'reassign' && (
        <TicketReasonField
          id="ticket-reassign-reason"
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
