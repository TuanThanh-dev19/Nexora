import { useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Form,
  Row,
  Spinner,
} from 'react-bootstrap'
import { FiSave, FiUser } from 'react-icons/fi'
import { UserStatusBadge } from '../../administration/components/UserStatusBadge'
import { TechnicianStatusBadge } from './TechnicianStatusBadge'
import { formatTechnicianError, isConflictError } from '../api/technicianError'
import { useUpdateTechnicianCapacity } from '../hooks/useTechnicians'

export function TechnicianProfilePanel({
  technician,
  readOnly = false,
  onConflict,
}) {
  const [available, setAvailable] = useState(Boolean(technician?.available))
  const [maxActiveTickets, setMaxActiveTickets] = useState(
    technician?.maxActiveTickets ?? 5,
  )
  const [touchCapacity, setTouchCapacity] = useState(false)
  const [feedback, setFeedback] = useState(null)

  const updateMutation = useUpdateTechnicianCapacity()

  // Validation: integer > 0
  const numCapacity = Number(maxActiveTickets)
  const isCapacityValid =
    maxActiveTickets !== '' &&
    !isNaN(numCapacity) &&
    Number.isInteger(numCapacity) &&
    numCapacity > 0

  const capacityError =
    !isCapacityValid
      ? 'Sức chứa tối đa phải là số nguyên lớn hơn 0.'
      : null

  const isDirty =
    technician &&
    (Boolean(available) !== Boolean(technician.available) ||
      numCapacity !== Number(technician.maxActiveTickets))

  const handleSave = async (e) => {
    e.preventDefault()
    if (!isCapacityValid || !isDirty || readOnly) return

    setFeedback(null)
    try {
      await updateMutation.mutateAsync({
        technicianId: technician.technicianId,
        payload: {
          available: Boolean(available),
          maxActiveTickets: numCapacity,
          version: technician.version,
        },
      })
      setFeedback({
        type: 'success',
        message: 'Đã cập nhật tính sẵn sàng và sức chứa tối đa.',
      })
    } catch (err) {
      if (isConflictError(err)) {
        if (onConflict) onConflict(err)
      } else {
        setFeedback({
          type: 'danger',
          message: formatTechnicianError(err),
        })
      }
    }
  }

  const roles = technician.roles ?? ['TECHNICIAN']

  return (
    <Card className="shadow-sm mb-4">
      <Card.Header className="d-flex align-items-center justify-content-between py-3">
        <div className="d-flex align-items-center gap-2">
          <FiUser className="fs-5 text-primary" aria-hidden="true" />
          <h2 className="h5 mb-0">Hồ sơ & Tính sẵn sàng</h2>
        </div>
        <TechnicianStatusBadge available={readOnly ? technician.available : available} />
      </Card.Header>

      <Card.Body>
        {feedback && (
          <Alert
            variant={feedback.type}
            dismissible
            onClose={() => setFeedback(null)}
            className="mb-3"
          >
            {feedback.message}
          </Alert>
        )}

        {/* Identity information */}
        <Row className="mb-4 g-3 bg-light p-3 rounded border">
          <Col xs={12} sm={6} md={3}>
            <span className="d-block text-secondary small fw-semibold">Họ và tên</span>
            <strong className="fs-6">{technician.fullName}</strong>
          </Col>
          <Col xs={12} sm={6} md={4}>
            <span className="d-block text-secondary small fw-semibold">Email</span>
            <span className="text-break">{technician.email}</span>
          </Col>
          <Col xs={6} md={2}>
            <span className="d-block text-secondary small fw-semibold">Trạng thái</span>
            <UserStatusBadge status={technician.status} />
          </Col>
          <Col xs={6} md={3}>
            <span className="d-block text-secondary small fw-semibold">Vai trò</span>
            <div className="d-flex flex-wrap gap-1 mt-1">
              {roles.map((r) => (
                <Badge bg="light" text="dark" className="border" key={r}>
                  {r}
                </Badge>
              ))}
            </div>
          </Col>
        </Row>

        {/* Editable or read-only settings */}
        <Form onSubmit={handleSave}>
          <Row className="g-3 align-items-center">
            <Col xs={12} md={6}>
              <Form.Group controlId="technician-available-switch">
                <Form.Label className="fw-semibold">Trạng thái tiếp nhận phiếu</Form.Label>
                <Form.Check
                  type="switch"
                  id="technician-available-switch"
                  label={
                    available
                      ? 'Sẵn sàng tiếp nhận phiếu yêu cầu mới'
                      : 'Tạm ngưng tiếp nhận phiếu yêu cầu'
                  }
                  checked={available}
                  disabled={readOnly || updateMutation.isPending}
                  onChange={(e) => setAvailable(e.target.checked)}
                />
              </Form.Group>
            </Col>

            <Col xs={12} md={6}>
              <Form.Group controlId="technician-max-tickets">
                <Form.Label className="fw-semibold">
                  Sức chứa tối đa (số phiếu xử lý đồng thời)
                </Form.Label>
                {readOnly ? (
                  <p className="fs-5 fw-bold text-primary mb-0">
                    {technician.maxActiveTickets} <span className="fs-6 fw-normal text-secondary">phiếu</span>
                  </p>
                ) : (
                  <>
                    <Form.Control
                      type="number"
                      min={1}
                      step={1}
                      value={maxActiveTickets}
                      isInvalid={touchCapacity && !isCapacityValid}
                      disabled={updateMutation.isPending}
                      onChange={(e) => {
                        setTouchCapacity(true)
                        setMaxActiveTickets(e.target.value)
                      }}
                      onBlur={() => setTouchCapacity(true)}
                    />
                    <Form.Control.Feedback type="invalid">
                      {capacityError}
                    </Form.Control.Feedback>
                    <Form.Text className="text-secondary small">
                      Số lượng phiếu yêu cầu tối đa kỹ thuật viên có thể đảm nhận cùng một thời điểm.
                    </Form.Text>
                  </>
                )}
              </Form.Group>
            </Col>
          </Row>

          {!readOnly && (
            <div className="d-flex justify-content-end mt-4 pt-3 border-top">
              <Button
                type="submit"
                variant="primary"
                disabled={!isDirty || !isCapacityValid || updateMutation.isPending}
                className="d-inline-flex align-items-center gap-1"
              >
                {updateMutation.isPending ? (
                  <>
                    <Spinner size="sm" animation="border" /> Đang lưu...
                  </>
                ) : (
                  <>
                    <FiSave aria-hidden="true" /> Lưu cấu hình
                  </>
                )}
              </Button>
            </div>
          )}
        </Form>
      </Card.Body>
    </Card>
  )
}
