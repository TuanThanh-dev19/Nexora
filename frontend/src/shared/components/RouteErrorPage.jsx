import { Alert, Button, Container } from 'react-bootstrap'
import { Link, useRouteError } from 'react-router'

export function RouteErrorPage() {
  const error = useRouteError()
  return (
    <Container className="page-container">
      <h1 className="h3">Không thể mở trang lúc này</h1>
      <Alert variant="danger" role="alert">
        {error?.status === 404
          ? 'Không tìm thấy nội dung.'
          : 'Đã có lỗi khi tải trang. Đường dẫn hiện tại được giữ nguyên.'}
      </Alert>
      <div className="d-flex flex-wrap gap-2">
        <Button onClick={() => window.location.reload()}>Thử lại</Button>
        <Button as={Link} to="/" variant="outline-secondary">
          Về trang chủ
        </Button>
      </div>
    </Container>
  )
}
