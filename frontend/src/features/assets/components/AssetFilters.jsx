import { Button, Col, Form, Row, Stack } from 'react-bootstrap'
import { TicketLocationFilter } from '../../tickets/components/TicketLocationFilter'
import {
  ASSET_STATUS_CODES,
  ASSET_STATUS_LABELS,
} from '../constants/assetStatuses'
import { ASSET_SORT_OPTIONS } from '../constants/assetSorts'

export function AssetFilters({
  locationId,
  status,
  sort,
  size,
  onChange,
  onReset,
}) {
  const isFiltered = Boolean(
    locationId ||
    status ||
    (sort && sort !== 'createdAt,desc') ||
    (size && size !== '20'),
  )

  return (
    <div className="ticket-filters mb-4">
      <Row className="g-3 align-items-end">
        <Col xs={12} lg={4}>
          <TicketLocationFilter
            locationId={locationId}
            onChange={(nextId) => onChange({ locationId: nextId })}
          />
        </Col>

        <Col xs={6} sm={4} lg={3}>
          <Form.Label
            htmlFor="asset-status-filter"
            className="small fw-semibold"
          >
            Trạng thái
          </Form.Label>
          <Form.Select
            id="asset-status-filter"
            value={status}
            onChange={(e) => onChange({ status: e.target.value })}
          >
            <option value="">Tất cả trạng thái</option>
            {Object.values(ASSET_STATUS_CODES).map((code) => (
              <option key={code} value={code}>
                {ASSET_STATUS_LABELS[code] ?? code}
              </option>
            ))}
          </Form.Select>
        </Col>

        <Col xs={6} sm={4} lg={3}>
          <Form.Label htmlFor="asset-sort-select" className="small fw-semibold">
            Sắp xếp
          </Form.Label>
          <Form.Select
            id="asset-sort-select"
            value={sort}
            onChange={(e) => onChange({ sort: e.target.value })}
          >
            {ASSET_SORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </Form.Select>
        </Col>

        <Col xs={6} sm={4} lg={2}>
          <Form.Label htmlFor="asset-size-select" className="small fw-semibold">
            Số lượng / trang
          </Form.Label>
          <Form.Select
            id="asset-size-select"
            value={size}
            onChange={(e) => onChange({ size: e.target.value })}
          >
            {[10, 20, 50, 100].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </Form.Select>
        </Col>
      </Row>

      {isFiltered && (
        <Stack direction="horizontal" gap={2} className="mt-3">
          <Button variant="outline-secondary" size="sm" onClick={onReset}>
            Đặt lại bộ lọc
          </Button>
        </Stack>
      )}
    </div>
  )
}
