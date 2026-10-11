import { Button, Card, Container } from 'react-bootstrap'
import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <Container className="page-container">
      <Card className="placeholder-card">
        <Card.Body>
          <p className="eyebrow">404 — KHÔNG TÌM THẤY</p>
          <h1 className="h3">Không tìm thấy trang</h1>
          <p className="text-secondary">
            Đường dẫn không tồn tại hoặc đã thay đổi. Địa chỉ được giữ nguyên để
            bạn kiểm tra lại.
          </p>
          <Button as={Link} to="/" variant="outline-primary">
            Về trang chủ
          </Button>
        </Card.Body>
      </Card>
    </Container>
  )
}
