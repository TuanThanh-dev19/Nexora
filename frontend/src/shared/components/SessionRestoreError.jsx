import { Alert, Button, Container } from 'react-bootstrap'
import { useAuth } from '../../features/auth/context/useAuth'

export function SessionRestoreError() {
  const { retrySession, logout } = useAuth()
  return (
    <Container className="page-container">
      <h1 className="h3">Chưa kiểm tra được phiên đăng nhập</h1>
      <Alert variant="danger">
        Không thể kết nối máy chủ. Đường dẫn và phiên của bạn được giữ lại; nội
        dung riêng tư chưa được tải.
      </Alert>
      <div className="d-flex flex-wrap gap-2">
        <Button onClick={retrySession}>Thử lại</Button>
        <Button variant="outline-secondary" onClick={logout}>
          Đăng xuất
        </Button>
      </div>
    </Container>
  )
}
