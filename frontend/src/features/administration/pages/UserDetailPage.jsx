import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import {
  Alert,
  Button,
  Card,
  Col,
  Container,
  Dropdown,
  Row,
} from 'react-bootstrap'
import {
  FiAlertCircle,
  FiArrowLeft,
  FiCheckCircle,
  FiClock,
  FiInfo,
  FiMail,
  FiShield,
  FiUser,
  FiUserCheck,
} from 'react-icons/fi'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { isAdminUserMockEnabled } from '../../../shared/config/env'
import {
  USER_STATUS_CODES,
  USER_STATUS_LABELS,
} from '../../../shared/constants/userStatuses'
import { adminUserApi, formatAdminUserError } from '../api/adminUserApi'
import { UserRoleBadges } from '../components/UserRoleBadges'
import { UserRolesModal } from '../components/UserRolesModal'
import { UserStatusBadge } from '../components/UserStatusBadge'
import { UserStatusModal } from '../components/UserStatusModal'
import { adminUserKeys, useAdminUser } from '../hooks/useAdminUsers'

export function UserDetailPage() {
  const { userId } = useParams()
  const queryClient = useQueryClient()
  const isMockMode = isAdminUserMockEnabled()

  const { data: user, isLoading, isError, error, refetch } = useAdminUser(userId)

  const [statusModalTarget, setStatusModalTarget] = useState(null)
  const [showRolesModal, setShowRolesModal] = useState(false)
  const [notice, setNotice] = useState(null)

  // Update status mutation
  const statusMutation = useMutation({
    mutationFn: (payload) => adminUserApi.updateStatus(userId, payload),
    onSuccess: (updatedUser) => {
      setStatusModalTarget(null)
      setNotice({
        type: 'success',
        message: `Đã cập nhật trạng thái tài khoản thành ${USER_STATUS_LABELS[updatedUser.status] ?? updatedUser.status}.`,
      })
      queryClient.invalidateQueries({ queryKey: adminUserKeys.all })
    },
  })

  // Update roles mutation
  const rolesMutation = useMutation({
    mutationFn: (payload) => adminUserApi.updateRoles(userId, payload),
    onSuccess: (updatedUser) => {
      setShowRolesModal(false)
      setNotice({
        type: 'success',
        message: `Đã cập nhật danh sách vai trò: ${updatedUser.roles?.join(', ')}.`,
      })
      queryClient.invalidateQueries({ queryKey: adminUserKeys.all })
    },
  })

  if (isLoading) {
    return (
      <Container className="page-container">
        <LoadingSpinner label="Đang tải thông tin người dùng..." />
      </Container>
    )
  }

  if (isError) {
    const isNotFound = error?.response?.status === 404
    return (
      <Container className="page-container">
        <div className="mb-4">
          <Button as={Link} to="/administration/users" variant="outline-secondary" size="sm">
            <FiArrowLeft aria-hidden="true" /> Danh sách người dùng
          </Button>
        </div>
        <Card className="p-4 border-danger">
          <Card.Body className="text-center py-4">
            <FiAlertCircle className="text-danger fs-1 mb-3" aria-hidden="true" />
            <h2 className="h4 mb-2">
              {isNotFound ? 'Không tìm thấy người dùng' : 'Lỗi khi tải thông tin'}
            </h2>
            <p className="text-muted mb-4">
              {isNotFound
                ? `Tài khoản với mã #${userId} không tồn tại hoặc đã bị xóa khỏi hệ thống.`
                : formatAdminUserError(error)}
            </p>
            <div className="d-flex justify-content-center gap-2">
              <Button as={Link} to="/administration/users" variant="outline-secondary">
                <FiArrowLeft aria-hidden="true" /> Về danh sách
              </Button>
              {!isNotFound && (
                <Button variant="primary" onClick={() => refetch()}>
                  Thử lại
                </Button>
              )}
            </div>
          </Card.Body>
        </Card>
      </Container>
    )
  }

  if (!user) return null

  const formatDateTime = (isoString) => {
    if (!isoString) return '—'
    try {
      const date = new Date(isoString)
      return date.toLocaleString('vi-VN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoString
    }
  }

  return (
    <Container className="page-container">
      {/* Back button */}
      <div className="mb-3">
        <Button as={Link} to="/administration/users" variant="outline-secondary" size="sm">
          <FiArrowLeft aria-hidden="true" /> Quay lại danh sách người dùng
        </Button>
      </div>

      {/* Page Heading */}
      <div className="page-heading">
        <div>
          <p className="eyebrow">HỒ SƠ TÀI KHOẢN</p>
          <div className="d-flex align-items-center gap-3 flex-wrap">
            <h1 className="h2 mb-0">{user.fullName}</h1>
            <UserStatusBadge status={user.status} />
          </div>
          <p className="text-secondary mt-1 mb-0">ID tài khoản: #{user.id}</p>
        </div>
        <span className="page-heading-icon" aria-hidden="true">
          <FiUser />
        </span>
      </div>

      {/* Mock Mode Alert */}
      {isMockMode && (
        <Alert variant="warning" className="d-flex align-items-center gap-2 mb-3">
          <FiInfo className="flex-shrink-0 fs-5" aria-hidden="true" />
          <div>
            <strong>Chế độ mô phỏng (Mock Mode):</strong> Các thay đổi sẽ chỉ cập nhật trên bộ nhớ
            tạm thời của phiên thử nghiệm hiện tại.
          </div>
        </Alert>
      )}

      {/* Notification notice */}
      {notice && (
        <Alert
          variant={notice.type}
          dismissible
          onClose={() => setNotice(null)}
          className="d-flex align-items-center gap-2 mb-3"
        >
          <FiCheckCircle className="flex-shrink-0" aria-hidden="true" />
          <div>{notice.message}</div>
        </Alert>
      )}

      {/* Details Card */}
      <Row className="g-4 mb-4">
        <Col lg={8}>
          <Card className="h-100 shadow-sm">
            <Card.Header className="bg-transparent border-bottom py-3">
              <h2 className="h5 mb-0 d-flex align-items-center gap-2">
                <FiUser aria-hidden="true" /> Thông tin cơ bản
              </h2>
            </Card.Header>
            <Card.Body>
              <Row className="g-3">
                <Col sm={6}>
                  <div className="text-muted small mb-1">Họ và tên</div>
                  <div className="fw-semibold fs-6">{user.fullName}</div>
                </Col>
                <Col sm={6}>
                  <div className="text-muted small mb-1">Địa chỉ Email</div>
                  <div className="d-flex align-items-center gap-2">
                    <FiMail className="text-muted" aria-hidden="true" />
                    <code>{user.email}</code>
                  </div>
                </Col>
                <Col sm={6}>
                  <div className="text-muted small mb-1">Trạng thái tài khoản</div>
                  <UserStatusBadge status={user.status} />
                </Col>
                <Col sm={6}>
                  <div className="text-muted small mb-1">Mã định danh (User ID)</div>
                  <span className="badge bg-light text-dark border">#{user.id}</span>
                </Col>
                <Col xs={12}>
                  <hr className="my-2" />
                </Col>
                <Col sm={6}>
                  <div className="text-muted small mb-1 d-flex align-items-center gap-1">
                    <FiClock aria-hidden="true" /> Ngày tạo tài khoản
                  </div>
                  <div>{formatDateTime(user.createdAt)}</div>
                </Col>
                <Col sm={6}>
                  <div className="text-muted small mb-1 d-flex align-items-center gap-1">
                    <FiClock aria-hidden="true" /> Cập nhật lần cuối
                  </div>
                  <div>{formatDateTime(user.updatedAt)}</div>
                </Col>
              </Row>
            </Card.Body>
          </Card>
        </Col>

        {/* Roles & Quick Actions Card */}
        <Col lg={4}>
          <Card className="h-100 shadow-sm">
            <Card.Header className="bg-transparent border-bottom py-3">
              <h2 className="h5 mb-0 d-flex align-items-center gap-2">
                <FiShield aria-hidden="true" /> Phân quyền & Thao tác
              </h2>
            </Card.Header>
            <Card.Body className="d-flex flex-column justify-content-between">
              <div className="mb-4">
                <div className="text-muted small mb-2">Vai trò hiện tại:</div>
                <UserRoleBadges roles={user.roles} />
                <p className="text-muted small mt-3">
                  Vai trò quyết định phạm vi truy cập các module và hành động được phép trên hệ thống Nexora.
                </p>
              </div>

              <div className="d-flex flex-column gap-2 pt-3 border-top">
                <Button
                  variant="outline-primary"
                  className="w-100 justify-content-start"
                  onClick={() => {
                    rolesMutation.reset()
                    setShowRolesModal(true)
                  }}
                  id="btn-open-manage-roles"
                >
                  <FiShield aria-hidden="true" /> Quản lý vai trò
                </Button>

                <Dropdown className="w-100">
                  <Dropdown.Toggle
                    variant="outline-secondary"
                    className="w-100 justify-content-between d-flex align-items-center"
                    id="dropdown-change-status"
                  >
                    <span className="d-inline-flex align-items-center gap-2">
                      <FiUserCheck aria-hidden="true" /> Thay đổi trạng thái
                    </span>
                  </Dropdown.Toggle>
                  <Dropdown.Menu className="w-100 shadow-sm">
                    {Object.values(USER_STATUS_CODES).map((st) => (
                      <Dropdown.Item
                        key={st}
                        active={user.status === st}
                        disabled={user.status === st}
                        onClick={() => {
                          statusMutation.reset()
                          setStatusModalTarget(st)
                        }}
                      >
                        Đổi thành: <strong>{USER_STATUS_LABELS[st] ?? st}</strong>
                      </Dropdown.Item>
                    ))}
                  </Dropdown.Menu>
                </Dropdown>
              </div>
            </Card.Body>
          </Card>
        </Col>
      </Row>

      {/* Change Status Modal */}
      {statusModalTarget && (
        <UserStatusModal
          show={Boolean(statusModalTarget)}
          user={user}
          newStatus={statusModalTarget}
          onHide={() => setStatusModalTarget(null)}
          onConfirm={({ status, version }) =>
            statusMutation.mutate({ status, version })
          }
          isPending={statusMutation.isPending}
          error={statusMutation.error}
        />
      )}

      {/* Manage Roles Modal */}
      <UserRolesModal
        key={`${user.id}-${user.roles?.join(',')}`}
        show={showRolesModal}
        user={user}
        onHide={() => setShowRolesModal(false)}
        onConfirm={({ roles }) => rolesMutation.mutate({ roles })}
        isPending={rolesMutation.isPending}
        error={rolesMutation.error}
      />
    </Container>
  )
}

