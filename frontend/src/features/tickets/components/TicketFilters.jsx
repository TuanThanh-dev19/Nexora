import { Button, Col, Form, Row } from 'react-bootstrap'
import { FiFilter, FiRotateCcw } from 'react-icons/fi'
import { PRIORITY_OPTIONS } from '../../../shared/constants/priorities'
import { TICKET_STATUS_OPTIONS } from '../constants/ticketStatuses'
import { TicketLocationFilter } from './TicketLocationFilter'

export function TicketFilters({
  status = '',
  statusOptions = TICKET_STATUS_OPTIONS,
  priority = '',
  locationId = '',
  onChange,
  onReset,
  children,
}) {
  return (
    <div className="ticket-filter-panel">
      <div className="filter-heading">
        <FiFilter aria-hidden="true" />
        <h2>Bộ lọc</h2>
      </div>
      <Row className="g-3 align-items-end">
        <Col xs={6} xl={3}>
          <Form.Label htmlFor="ticket-status">Trạng thái</Form.Label>
          <Form.Select
            id="ticket-status"
            value={status}
            onChange={(event) => onChange({ status: event.target.value })}
          >
            <option value="">Tất cả</option>
            {statusOptions.map(({ value, label }) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Form.Select>
        </Col>
        <Col xs={6} xl={3}>
          <Form.Label htmlFor="ticket-priority">Mức ưu tiên</Form.Label>
          <Form.Select
            id="ticket-priority"
            value={priority}
            onChange={(event) => onChange({ priority: event.target.value })}
          >
            <option value="">Tất cả</option>
            {PRIORITY_OPTIONS.map(({ value, label }) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Form.Select>
        </Col>
        {children}
      </Row>
      <div className="ticket-filter-footer">
        <TicketLocationFilter
          locationId={locationId}
          onChange={(value) => onChange({ locationId: value })}
        />
        {onReset && (
          <Button variant="outline-secondary" size="sm" onClick={onReset}>
            <FiRotateCcw aria-hidden="true" /> Đặt lại bộ lọc
          </Button>
        )}
      </div>
    </div>
  )
}
