import { Alert, Button, Card, Container } from 'react-bootstrap'
import { Link } from 'react-router'

/** Integration entry only: never presents an unfinished dependency as a demo. */
export function PendingFeaturePage({ title, issue }) {
  return (
    <Container className="page-container">
      <div className="page-heading">
        <div>
          <p className="eyebrow">CHỜ TÍCH HỢP UI</p>
          <h1 className="h2">{title}</h1>
        </div>
      </div>
      <Card>
        <Card.Body>
          <Alert variant="warning">
            Chức năng này chưa hoàn tất trong issue #{issue}. Đây không phải màn
            CRUD hoặc dữ liệu mock đã nghiệm thu.
          </Alert>
          <p>
            Route và quyền truy cập đã chuẩn bị. Cần tích hợp màn hình của người
            phụ trách trước khi nghiệm thu UI MVP #62.
          </p>
          <Button as={Link} to="/dashboard" variant="outline-primary">
            Quay lại Tổng quan
          </Button>
        </Card.Body>
      </Card>
    </Container>
  )
}
