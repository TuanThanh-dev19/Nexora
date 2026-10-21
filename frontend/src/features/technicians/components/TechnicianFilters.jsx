import { Button, Col, Form, Row } from 'react-bootstrap'
import { FiRotateCcw } from 'react-icons/fi'
import { AVAILABILITY_FILTER_OPTIONS } from '../constants/technicianConstants'

export function TechnicianFilters({
  query = '',
  available = '',
  onChange,
  onReset,
}) {
  const isFiltered = query.trim() !== '' || available !== ''

  return (
    <div className="filter-card p-3 mb-4 rounded border bg-white">
      <Row className="g-3 align-items-end">
        <Col xs={12} md={6} lg={5}>
          <Form.Label htmlFor="technician-search" className="fw-semibold small">
            Tìm kiếm kỹ thuật viên
          </Form.Label>
          <div className="position-relative">
            <Form.Control
              id="technician-search"
              type="text"
              placeholder="Nhập tên hoặc email..."
              value={query}
              onChange={(e) => onChange({ query: e.target.value, page: 0 })}
            />
          </div>
        </Col>

        <Col xs={12} sm={6} lg={4}>
          <Form.Label htmlFor="technician-available" className="fw-semibold small">
            Trạng thái sẵn sàng
          </Form.Label>
          <Form.Select
            id="technician-available"
            value={available}
            onChange={(e) => onChange({ available: e.target.value, page: 0 })}
          >
            {AVAILABILITY_FILTER_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </Form.Select>
        </Col>

        {isFiltered && (
          <Col xs="auto">
            <Button
              variant="outline-secondary"
              onClick={onReset}
              className="d-inline-flex align-items-center gap-1"
            >
              <FiRotateCcw aria-hidden="true" /> Đặt lại
            </Button>
          </Col>
        )}
      </Row>
    </div>
  )
}
