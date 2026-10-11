import { Badge, Button, Card, Container } from 'react-bootstrap'
import { Link } from 'react-router'
import { FiArrowLeft, FiLock, FiShield } from 'react-icons/fi'
import { useAuth } from '../../features/auth/context/useAuth'

export function ForbiddenPage() {
  const { user } = useAuth()

  return (
    <Container className="page-container">
      <Card className="placeholder-card">
        <Card.Body className="text-center py-5">
          <span className="placeholder-icon mb-3" aria-hidden="true">
            <FiLock />
          </span>
          <div>
            <Badge bg="danger" className="mb-3">
              403 — Từ chối truy cập
            </Badge>
          </div>
          <h1 className="h3 mb-3">Bạn không có quyền truy cập trang này</h1>
          <p className="text-secondary mb-3 max-w-md mx-auto" style={{ maxWidth: '520px' }}>
            Vai trò hiện tại của bạn không được cấp phép để thực hiện tác vụ hoặc xem nội dung này.
            Phiên đăng nhập của bạn vẫn còn hiệu lực.
          </p>
          {user && (
            <div className="d-inline-flex align-items-center gap-2 px-3 py-2 rounded-3 bg-light mb-4 border">
              <FiShield className="text-muted" aria-hidden="true" />
              <small className="text-muted">
                Tài khoản: <strong>{user.fullName ?? user.email}</strong> · Vai trò:{' '}
                <strong>{user.roles?.join(', ') || 'Không có'}</strong>
              </small>
            </div>
          )}
          <div>
            <Button as={Link} to="/dashboard" variant="outline-primary">
              <FiArrowLeft aria-hidden="true" /> Quay lại Tổng quan
            </Button>
          </div>
        </Card.Body>
      </Card>
    </Container>
  )
}
