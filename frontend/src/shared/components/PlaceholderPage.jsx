import { Badge, Card, Container } from 'react-bootstrap'
import { FiArrowLeft, FiLayers, FiLock } from 'react-icons/fi'

export function PlaceholderPage({ title, description }) {
  const restricted = title.startsWith('403')
  const Icon = restricted ? FiLock : FiLayers
  return (
    <Container className="page-container">
      <Card className="placeholder-card">
        <Card.Body>
          <span className="placeholder-icon">
            <Icon aria-hidden="true" />
          </span>
          <Badge bg="light" text="dark" className="mb-3">
            {restricted ? 'Quyền truy cập' : 'Đang phát triển'}
          </Badge>
          <h1 className="h3">{title}</h1>
          <p className="text-secondary mb-0">{description}</p>
          <a href="/" className="btn btn-outline-secondary mt-4">
            <FiArrowLeft aria-hidden="true" /> Về trang chủ
          </a>
        </Card.Body>
      </Card>
    </Container>
  )
}
