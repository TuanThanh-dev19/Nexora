import { Alert, Button, Modal } from 'react-bootstrap'
import { FiAlertTriangle } from 'react-icons/fi'
import { USER_STATUS_CODES, USER_STATUS_LABELS } from '../../../shared/constants/userStatuses'
import { formatAdminUserError } from '../api/adminUserApi'
import { UserStatusBadge } from './UserStatusBadge'

const statusDescriptions = {
  [USER_STATUS_CODES.ACTIVE]:
    'Kích hoạt lại tài khoản. Người dùng sẽ có thể đăng nhập và sử dụng hệ thống bình thường.',
  [USER_STATUS_CODES.LOCKED]:
    'Tạm khóa tài khoản. Người dùng sẽ không thể đăng nhập hay tạo mới yêu cầu cho đến khi được mở lại.',
  [USER_STATUS_CODES.DISABLED]:
    'Vô hiệu hóa tài khoản. Mọi phiên đăng nhập hiện tại sẽ bị hủy và tài khoản không thể truy cập hệ thống.',
}

const statusActionLabels = {
  [USER_STATUS_CODES.ACTIVE]: 'Kích hoạt tài khoản',
  [USER_STATUS_CODES.LOCKED]: 'Khóa tài khoản',
  [USER_STATUS_CODES.DISABLED]: 'Vô hiệu hóa tài khoản',
}

const statusButtonVariants = {
  [USER_STATUS_CODES.ACTIVE]: 'success',
  [USER_STATUS_CODES.LOCKED]: 'warning',
  [USER_STATUS_CODES.DISABLED]: 'danger',
}

export function UserStatusModal({
  show,
  user,
  newStatus,
  onHide,
  onConfirm,
  isPending = false,
  error = null,
}) {
  if (!user || !newStatus) return null

  const isDestructive = newStatus === USER_STATUS_CODES.DISABLED || newStatus === USER_STATUS_CODES.LOCKED

  return (
    <Modal
      show={show}
      onHide={() => !isPending && onHide()}
      backdrop="static"
      centered
    >
      <Modal.Header closeButton={!isPending}>
        <Modal.Title as="h2" className="h5 mb-0 d-flex align-items-center gap-2">
          {isDestructive && <FiAlertTriangle className="text-warning" aria-hidden="true" />}
          <span>Xác nhận đổi trạng thái tài khoản</span>
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        {error && (
          <Alert variant="danger" className="mb-3">
            {formatAdminUserError(error)}
          </Alert>
        )}

        <p>
          Bạn có chắc chắn muốn thay đổi trạng thái của tài khoản{' '}
          <strong>{user.fullName}</strong> (<code>{user.email}</code>)?
        </p>

        <div className="p-3 mb-3 bg-light rounded border">
          <div className="d-flex align-items-center justify-content-between mb-2">
            <span className="text-muted small">Trạng thái hiện tại:</span>
            <UserStatusBadge status={user.status} />
          </div>
          <div className="d-flex align-items-center justify-content-between">
            <span className="text-muted small">Trạng thái mới:</span>
            <UserStatusBadge status={newStatus} />
          </div>
        </div>

        <Alert variant={newStatus === USER_STATUS_CODES.ACTIVE ? 'info' : 'warning'} className="mb-0">
          <strong>Ý nghĩa:</strong> {statusDescriptions[newStatus] ?? USER_STATUS_LABELS[newStatus]}
        </Alert>
      </Modal.Body>
      <Modal.Footer>
        <Button
          variant="outline-secondary"
          disabled={isPending}
          onClick={onHide}
        >
          Hủy bỏ
        </Button>
        <Button
          variant={statusButtonVariants[newStatus] ?? 'primary'}
          disabled={isPending}
          onClick={() => onConfirm({ status: newStatus, version: user.version })}
          id="btn-confirm-status-change"
        >
          {isPending ? 'Đang cập nhật…' : statusActionLabels[newStatus] ?? 'Xác nhận thay đổi'}
        </Button>
      </Modal.Footer>
    </Modal>
  )
}
