import { useState } from 'react'
import { Alert, Button, Col, Form, Modal, Row } from 'react-bootstrap'
import { FiShield } from 'react-icons/fi'
import { ROLE_OPTIONS } from '../../../shared/constants/roles'
import { formatAdminUserError } from '../api/adminUserApi'
import { UserRoleBadges } from './UserRoleBadges'

export function UserRolesModal({
  show,
  user,
  onHide,
  onConfirm,
  isPending = false,
  error = null,
}) {
  const [selectedRoles, setSelectedRoles] = useState(() => (user?.roles ? [...user.roles] : []))
  const [validationError, setValidationError] = useState('')


  if (!user) return null

  const handleToggleRole = (roleCode) => {
    setValidationError('')
    if (selectedRoles.includes(roleCode)) {
      setSelectedRoles(selectedRoles.filter((r) => r !== roleCode))
    } else {
      setSelectedRoles([...selectedRoles, roleCode])
    }
  }

  const handleSave = () => {
    if (selectedRoles.length === 0) {
      setValidationError('Người dùng phải có ít nhất một vai trò được chỉ định.')
      return
    }
    onConfirm({ roles: selectedRoles })
  }

  return (
    <Modal
      show={show}
      onHide={() => !isPending && onHide()}
      backdrop="static"
      centered
    >
      <Modal.Header closeButton={!isPending}>
        <Modal.Title as="h2" className="h5 mb-0 d-flex align-items-center gap-2">
          <FiShield aria-hidden="true" />
          <span>Phân quyền vai trò người dùng</span>
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        {error && (
          <Alert variant="danger" className="mb-3">
            {formatAdminUserError(error)}
          </Alert>
        )}

        {validationError && (
          <Alert variant="warning" className="mb-3">
            {validationError}
          </Alert>
        )}

        <div className="mb-3">
          <p className="mb-1">
            Thiết lập vai trò cho: <strong>{user.fullName}</strong>
          </p>
          <p className="text-muted small">
            Email: <code>{user.email}</code>
          </p>
        </div>

        <div className="p-3 mb-3 bg-light rounded border">
          <div className="text-muted small mb-2">Vai trò hiện tại:</div>
          <UserRoleBadges roles={user.roles} />
        </div>

        <Form.Group className="mb-3">
          <Form.Label className="fw-semibold">
            Chọn các vai trò áp dụng: <span className="text-danger">*</span>
          </Form.Label>
          <div className="p-3 border rounded">
            <Row className="g-2">
              {ROLE_OPTIONS.map(({ value, label }) => (
                <Col sm={6} key={value}>
                  <Form.Check
                    type="checkbox"
                    id={`assign-role-${value}`}
                    label={label}
                    checked={selectedRoles.includes(value)}
                    disabled={isPending}
                    onChange={() => handleToggleRole(value)}
                  />
                </Col>
              ))}
            </Row>
          </div>
          <Form.Text className="text-muted">
            Chọn ít nhất một vai trò. Thay đổi sẽ có hiệu lực ngay trong phiên làm việc tiếp theo của người dùng.
          </Form.Text>
        </Form.Group>
      </Modal.Body>
      <Modal.Footer>
        <Button
          variant="outline-secondary"
          disabled={isPending}
          onClick={onHide}
        >
          Hủy
        </Button>
        <Button
          variant="primary"
          disabled={isPending}
          onClick={handleSave}
          id="btn-confirm-save-roles"
        >
          {isPending ? 'Đang lưu…' : 'Lưu thay đổi vai trò'}
        </Button>
      </Modal.Footer>
    </Modal>
  )
}
