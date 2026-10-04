import { Container, Spinner } from 'react-bootstrap'

export function SessionCheckingSpinner() {
  return (
    <Container className="d-flex justify-content-center align-items-center vh-100">
      <div className="text-center">
        <Spinner animation="border" variant="primary" role="status" />
        <p className="mt-3 text-muted">Đang kiểm tra phiên đăng nhập...</p>
      </div>
    </Container>
  )
}
