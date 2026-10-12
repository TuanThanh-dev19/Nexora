import { useState } from 'react'
import { Alert, Button, ListGroup, Stack } from 'react-bootstrap'
import { useLocation, useLocations } from '../../locations/hooks/useLocations'

export function TicketLocationFilter({ locationId, onChange }) {
  const [expanded, setExpanded] = useState(false)
  const [trail, setTrail] = useState([])
  const selected = useLocation(locationId)
  const children = useLocations(trail.at(-1)?.id, expanded)
  const label = (location) =>
    `${location.name} (${location.code})${location.active ? '' : ' — Ngừng sử dụng'}`
  return (
    <div className="ticket-location-filter">
      <Stack direction="horizontal" gap={2} className="flex-wrap">
        <span className="location-label">Địa điểm:</span>
        <Button
          variant="outline-secondary"
          aria-expanded={expanded}
          onClick={() => setExpanded(!expanded)}
        >
          {locationId
            ? selected.data
              ? label(selected.data)
              : 'Địa điểm đã chọn'
            : 'Chọn địa điểm'}
        </Button>
        {locationId && (
          <Button variant="link" onClick={() => onChange('')}>
            Xóa lọc địa điểm
          </Button>
        )}
      </Stack>
      {selected.isError && (
        <Alert variant="warning" className="mt-2">
          Không tải được tên địa điểm đã chọn. Bộ lọc vẫn được giữ.
        </Alert>
      )}
      {expanded && (
        <div className="location-browser">
          <p className="text-secondary small">
            Chỉ lọc phiếu tại đúng địa điểm đã chọn.
          </p>
          {trail.length > 0 && (
            <Button variant="link" onClick={() => setTrail(trail.slice(0, -1))}>
              Quay lên
            </Button>
          )}
          <p className="fw-semibold">{trail.at(-1)?.name ?? 'Các cơ sở'}</p>
          {children.isPending ? (
            <p role="status">Đang tải địa điểm...</p>
          ) : children.isError ? (
            <Alert variant="warning">
              Không tải được địa điểm.{' '}
              <Button variant="link" onClick={() => children.refetch()}>
                Thử tải địa điểm lại
              </Button>
            </Alert>
          ) : children.data?.length === 0 ? (
            <p>Không có địa điểm con.</p>
          ) : (
            <ListGroup>
              {children.data?.map((location) => (
                <ListGroup.Item key={location.id}>
                  <Stack
                    direction="horizontal"
                    gap={2}
                    className="justify-content-between flex-wrap"
                  >
                    <span>{label(location)}</span>
                    <span>
                      <Button
                        size="sm"
                        variant="outline-primary"
                        aria-label={`Chọn ${location.name}`}
                        onClick={() => {
                          onChange(String(location.id))
                          setExpanded(false)
                        }}
                      >
                        Chọn
                      </Button>{' '}
                      {!['ROOM', 'AREA'].includes(location.type) && (
                        <Button
                          size="sm"
                          variant="link"
                          aria-label={`Xem bên trong ${location.name}`}
                          onClick={() => setTrail([...trail, location])}
                        >
                          Xem bên trong
                        </Button>
                      )}
                    </span>
                  </Stack>
                </ListGroup.Item>
              ))}
            </ListGroup>
          )}
        </div>
      )}
    </div>
  )
}
