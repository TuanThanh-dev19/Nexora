import { Card, Col, Container, Row } from 'react-bootstrap'
import {
  FiArrowRight,
  FiBarChart2,
  FiClipboard,
  FiGrid,
  FiLayers,
  FiMapPin,
  FiPackage,
} from 'react-icons/fi'

const modules = [
  [
    'Ticket workflow',
    'Tra cứu phiếu yêu cầu, thông tin sự cố và lịch sử xử lý trong cùng một nơi.',
    FiClipboard,
  ],
  [
    'Equipment',
    'Không gian dành cho thiết bị, địa điểm và lịch sử bảo trì của campus.',
    FiPackage,
  ],
  [
    'Dynamic forms',
    'Biểu mẫu theo từng loại sự cố, giữ đúng phiên bản và thông tin đã ghi nhận.',
    FiLayers,
  ],
  [
    'Dashboard',
    'Không gian tổng quan về phiếu yêu cầu, tiến độ xử lý và thời hạn SLA.',
    FiBarChart2,
  ],
]

export function HomePage() {
  return (
    <Container className="page-container home-page">
      <section className="home-hero">
        <div className="hero-copy">
          <p className="eyebrow">KẾT NỐI CAMPUS · CHĂM SÓC KHÔNG GIAN CHUNG</p>
          <h1>Nexora</h1>
          <h2>
            Mọi sự cố.
            <br />
            <span>Một nơi theo dõi.</span>
          </h2>
          <p className="hero-description">
            Thông tin rõ ràng hơn, phối hợp dễ dàng hơn. Cùng chăm sóc thiết bị
            và cơ sở vật chất để campus luôn sẵn sàng cho mỗi ngày.
          </p>
          <div className="hero-actions">
            <a href="/tickets" className="btn btn-primary">
              Xem phiếu yêu cầu <FiArrowRight aria-hidden="true" />
            </a>
            <a href="/dashboard" className="btn btn-outline-secondary">
              Khám phá tổng quan
            </a>
          </div>
        </div>
        <div className="campus-scene" aria-hidden="true">
          <div className="scene-orbit" />
          <div className="campus-emblem">
            <FiGrid />
            <span>
              Không gian chung.
              <br />
              Chăm sóc cùng nhau.
            </span>
          </div>
          <div className="scene-label scene-location">
            <FiMapPin /> Địa điểm
          </div>
          <div className="scene-label scene-ticket">
            <FiClipboard /> Phiếu yêu cầu
          </div>
          <div className="scene-label scene-form">
            <FiLayers /> Biểu mẫu
          </div>
          <span className="scene-dot dot-one" />
          <span className="scene-dot dot-two" />
        </div>
      </section>
      <div className="section-heading">
        <div>
          <p className="eyebrow">TRONG NEXORA</p>
          <h2>Một không gian quản lý chung</h2>
        </div>
        <p className="text-secondary mb-0">
          Thông tin cần thiết, ngay trong tầm tay.
        </p>
      </div>
      <Row className="g-3 module-grid">
        {modules.map(([title, description, Icon], index) => (
          <Col md={6} key={title}>
            <Card className="h-100 module-card">
              <Card.Body>
                <div className="module-card-top">
                  <span className="module-icon">
                    <Icon aria-hidden="true" />
                  </span>
                  <span className="module-number">0{index + 1}</span>
                </div>
                <Card.Title>{title}</Card.Title>
                <Card.Text className="text-secondary">{description}</Card.Text>
              </Card.Body>
            </Card>
          </Col>
        ))}
      </Row>
    </Container>
  )
}
