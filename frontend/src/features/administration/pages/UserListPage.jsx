import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import {
  Alert,
  Button,
  Card,
  Col,
  Container,
  Form,
  Row,
  Stack,
  Table,
} from 'react-bootstrap'
import {
  FiAlertCircle,
  FiArrowUpRight,
  FiCheckCircle,
  FiInfo,
  FiRotateCcw,
  FiShield,
  FiUserCheck,
  FiUserPlus,
  FiUsers,
} from 'react-icons/fi'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { isAdminUserMockEnabled } from '../../../shared/config/env'
import { ROLE_OPTIONS } from '../../../shared/constants/roles'
import {
  USER_STATUS_CODES,
  USER_STATUS_LABELS,
} from '../../../shared/constants/userStatuses'
import { adminUserApi, formatAdminUserError } from '../api/adminUserApi'
import { CreateUserModal } from '../components/CreateUserModal'
import { UserRoleBadges } from '../components/UserRoleBadges'
import { UserRolesModal } from '../components/UserRolesModal'
import { UserStatusBadge } from '../components/UserStatusBadge'
import { UserStatusModal } from '../components/UserStatusModal'
import { adminUserKeys, useAdminUsers } from '../hooks/useAdminUsers'

export function UserListPage() {
  const queryClient = useQueryClient()
  const isMockMode = isAdminUserMockEnabled()

  // Filter & pagination state
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [status, setStatus] = useState('')
  const [role, setRole] = useState('')

  // Modals state
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [statusModalUser, setStatusModalUser] = useState(null)
  const [statusModalTarget, setStatusModalTarget] = useState(null)
  const [rolesModalUser, setRolesModalUser] = useState(null)

  // Notification alerts
  const [notice, setNotice] = useState(null)

  // Query params object
  const queryParams = {
    page,
    size,
    ...(status ? { status } : {}),
    ...(role ? { role } : {}),
  }

  const { data, isLoading, isError, error, refetch } = useAdminUsers(queryParams)

  // Create user mutation
  const createMutation = useMutation({
    mutationFn: (payload) => adminUserApi.create(payload),
    onSuccess: (createdUser) => {
      setShowCreateModal(false)
      setNotice({
        type: 'success',
        message: `Đã tạo tài khoản thành công cho "${createdUser.fullName}" (${createdUser.email}) với vai trò: ${createdUser.roles?.join(', ')}.`,
      })
      queryClient.invalidateQueries({ queryKey: adminUserKeys.lists() })
    },
  })

  // Update status mutation
  const statusMutation = useMutation({
    mutationFn: ({ userId, payload }) => adminUserApi.updateStatus(userId, payload),
    onSuccess: (updatedUser) => {
      setStatusModalUser(null)
      setStatusModalTarget(null)
      setNotice({
        type: 'success',
        message: `Đã cập nhật trạng thái tài khoản "${updatedUser.fullName}" thành ${USER_STATUS_LABELS[updatedUser.status] ?? updatedUser.status}.`,
      })
      queryClient.invalidateQueries({ queryKey: adminUserKeys.all })
    },
  })

  // Update roles mutation
  const rolesMutation = useMutation({
    mutationFn: ({ userId, payload }) => adminUserApi.updateRoles(userId, payload),
    onSuccess: (updatedUser) => {
      setRolesModalUser(null)
      setNotice({
        type: 'success',
        message: `Đã cập nhật vai trò cho "${updatedUser.fullName}": ${updatedUser.roles?.join(', ')}.`,
      })
      queryClient.invalidateQueries({ queryKey: adminUserKeys.all })
    },
  })

  const handleResetFilters = () => {
    setStatus('')
    setRole('')
    setPage(0)
  }

  const handleOpenStatusModal = (user) => {
    // Default to the opposite/next status
    const nextStatus =
      user.status === USER_STATUS_CODES.ACTIVE
        ? USER_STATUS_CODES.LOCKED
        : USER_STATUS_CODES.ACTIVE
    setStatusModalUser(user)
    setStatusModalTarget(nextStatus)
  }

  return (
    <Container className="page-container">
      {/* Page Heading */}
      <div className="page-heading">
        <div>
          <p className="eyebrow">QUẢN TRỊ HỆ THỐNG</p>
          <h1 className="h2">Quản lý người dùng</h1>
          <p className="text-secondary mb-0">
            Tạo tài khoản, kiểm soát trạng thái hoạt động và phân quyền vai trò người dùng trong hệ thống.
          </p>
        </div>
        <div className="d-flex align-items-center gap-2">
          <Button
            variant="primary"
            onClick={() => {
              createMutation.reset()
              setShowCreateModal(true)
            }}
            id="btn-open-create-user"
          >
            <FiUserPlus aria-hidden="true" /> Tạo người dùng
          </Button>
          <span className="page-heading-icon" aria-hidden="true">
            <FiUsers />
          </span>
        </div>
      </div>

      {/* Mock Mode Alert Banner */}
      {isMockMode && (
        <Alert variant="warning" className="d-flex align-items-center gap-2 mb-3 shadow-sm">
          <FiInfo className="flex-shrink-0 fs-5" aria-hidden="true" />
          <div>
            <strong>Chế độ mô phỏng (Mock Mode):</strong> Các thao tác tạo, cập nhật trạng thái và vai trò
            chỉ lưu tạm thời trên bộ nhớ trình duyệt và không tác động đến backend thực tế.
          </div>
        </Alert>
      )}

      {/* Notice alert */}
      {notice && (
        <Alert
          variant={notice.type}
          dismissible
          onClose={() => setNotice(null)}
          className="d-flex align-items-center gap-2 mb-3"
        >
          {notice.type === 'success' && <FiCheckCircle className="flex-shrink-0" aria-hidden="true" />}
          <div>{notice.message}</div>
        </Alert>
      )}

      {/* Main card */}
      <Card className="ticket-list-card">
        <Card.Body className="ticket-list-body">
          {/* Filters Bar */}
          <div className="p-3 mb-4 bg-light rounded-3 border">
            <Row className="g-3 align-items-end">
              <Col xs={12} sm={6} md={3}>
                <Form.Label htmlFor="user-filter-status" className="small fw-semibold text-muted">
                  Trạng thái
                </Form.Label>
                <Form.Select
                  id="user-filter-status"
                  value={status}
                  onChange={(e) => {
                    setStatus(e.target.value)
                    setPage(0)
                  }}
                >
                  <option value="">Tất cả trạng thái</option>
                  {Object.entries(USER_STATUS_LABELS).map(([val, label]) => (
                    <option key={val} value={val}>
                      {label}
                    </option>
                  ))}
                </Form.Select>
              </Col>

              <Col xs={12} sm={6} md={3}>
                <Form.Label htmlFor="user-filter-role" className="small fw-semibold text-muted">
                  Vai trò
                </Form.Label>
                <Form.Select
                  id="user-filter-role"
                  value={role}
                  onChange={(e) => {
                    setRole(e.target.value)
                    setPage(0)
                  }}
                >
                  <option value="">Tất cả vai trò</option>
                  {ROLE_OPTIONS.map(({ value, label }) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </Form.Select>
              </Col>

              <Col xs={6} sm={6} md={3}>
                <Form.Label htmlFor="user-page-size" className="small fw-semibold text-muted">
                  Số lượng / trang
                </Form.Label>
                <Form.Select
                  id="user-page-size"
                  value={size}
                  onChange={(e) => {
                    setSize(Number(e.target.value))
                    setPage(0)
                  }}
                >
                  {[10, 20, 50].map((s) => (
                    <option key={s} value={s}>
                      {s} người dùng
                    </option>
                  ))}
                </Form.Select>
              </Col>

              <Col xs={6} sm={6} md={3} className="d-flex align-items-end">
                {(status || role) && (
                  <Button
                    variant="outline-secondary"
                    className="w-100"
                    onClick={handleResetFilters}
                  >
                    <FiRotateCcw aria-hidden="true" /> Đặt lại
                  </Button>
                )}
              </Col>
            </Row>
          </div>

          {/* Heading + count */}
          <div className="ticket-results-heading d-flex justify-content-between align-items-center mb-3">
            <h2 className="h5 mb-0">Danh sách tài khoản</h2>
            {!isError && data && (
              <span className="result-count text-muted small">
                {data.totalElements} tài khoản
              </span>
            )}
          </div>

          {/* Content state */}
          {isLoading && <LoadingSpinner label="Đang tải danh sách người dùng..." />}

          {isError && (
            <Alert variant="danger" className="d-flex align-items-center justify-content-between">
              <div className="d-flex align-items-center gap-2">
                <FiAlertCircle className="fs-5" aria-hidden="true" />
                <span>{formatAdminUserError(error)}</span>
              </div>
              <Button variant="outline-danger" size="sm" onClick={() => refetch()}>
                Thử lại
              </Button>
            </Alert>
          )}

          {!isLoading && !isError && data && (
            <>
              {data.content.length === 0 ? (
                <EmptyState message="Không tìm thấy người dùng nào phù hợp với bộ lọc." />
              ) : (
                <Table responsive hover className="align-middle ticket-table mb-4">
                  <thead>
                    <tr>
                      <th>Họ và tên</th>
                      <th>Email</th>
                      <th>Vai trò</th>
                      <th>Trạng thái</th>
                      <th className="text-end">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((user) => (
                      <tr key={user.id}>
                        <td>
                          <Link
                            to={`/administration/users/${user.id}`}
                            className="fw-semibold text-decoration-none text-dark d-inline-flex align-items-center gap-1"
                          >
                            <span>{user.fullName}</span>
                            <FiArrowUpRight className="text-muted small" aria-hidden="true" />
                          </Link>
                          {user.id != null && (
                            <div className="text-muted small">ID: #{user.id}</div>
                          )}
                        </td>
                        <td>
                          <code>{user.email}</code>
                        </td>
                        <td>
                          <UserRoleBadges roles={user.roles} />
                        </td>
                        <td>
                          <UserStatusBadge status={user.status} />
                        </td>
                        <td className="text-end">
                          <div className="d-inline-flex gap-1">
                            <Button
                              as={Link}
                              to={`/administration/users/${user.id}`}
                              size="sm"
                              variant="outline-primary"
                              aria-label={`Xem chi tiết ${user.fullName}`}
                            >
                              Chi tiết
                            </Button>
                            <Button
                              size="sm"
                              variant="outline-secondary"
                              onClick={() => handleOpenStatusModal(user)}
                              aria-label={`Đổi trạng thái ${user.fullName}`}
                            >
                              <FiUserCheck aria-hidden="true" /> Đổi trạng thái
                            </Button>
                            <Button
                              size="sm"
                              variant="outline-secondary"
                              onClick={() => {
                                rolesMutation.reset()
                                setRolesModalUser(user)
                              }}
                              aria-label={`Phân quyền ${user.fullName}`}
                            >
                              <FiShield aria-hidden="true" /> Vai trò
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              )}

              {/* Pagination */}
              <Stack
                direction="horizontal"
                gap={2}
                className="ticket-pagination justify-content-between flex-wrap"
                aria-label="Phân trang danh sách người dùng"
              >
                <span className="text-muted small">
                  {data.totalElements} người dùng · Trang {data.page + 1}/{Math.max(1, data.totalPages)}
                </span>
                <Stack direction="horizontal" gap={2}>
                  <Button
                    variant="outline-secondary"
                    size="sm"
                    disabled={data.page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Trang trước
                  </Button>
                  <Button
                    variant="outline-secondary"
                    size="sm"
                    disabled={data.page + 1 >= data.totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Trang sau
                  </Button>
                </Stack>
              </Stack>
            </>
          )}
        </Card.Body>
      </Card>

      {/* Create User Modal */}
      <CreateUserModal
        show={showCreateModal}
        onHide={() => setShowCreateModal(false)}
        onSubmit={(payload) => createMutation.mutate(payload)}
        isPending={createMutation.isPending}
        serverError={createMutation.error}
      />

      {/* Status Modal */}
      {statusModalUser && (
        <UserStatusModal
          show={Boolean(statusModalUser)}
          user={statusModalUser}
          newStatus={statusModalTarget}
          onHide={() => {
            setStatusModalUser(null)
            setStatusModalTarget(null)
          }}
          onConfirm={({ status, version }) =>
            statusMutation.mutate({
              userId: statusModalUser.id,
              payload: { status, version },
            })
          }
          isPending={statusMutation.isPending}
          error={statusMutation.error}
        />
      )}

      {/* Roles Modal */}
      {rolesModalUser && (
        <UserRolesModal
          key={`${rolesModalUser.id}-${rolesModalUser.roles?.join(',')}`}
          show={Boolean(rolesModalUser)}
          user={rolesModalUser}
          onHide={() => setRolesModalUser(null)}
          onConfirm={({ roles }) =>
            rolesMutation.mutate({
              userId: rolesModalUser.id,
              payload: { roles },
            })
          }
          isPending={rolesMutation.isPending}
          error={rolesMutation.error}
        />
      )}
    </Container>
  )
}
