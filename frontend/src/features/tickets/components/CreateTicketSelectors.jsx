import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Form, ListGroup } from 'react-bootstrap'
import { createTicketApi } from '../api/createTicketApi'

export function CreateLocationSelector({
  value,
  onChange,
  disabled,
  api = createTicketApi,
}) {
  const [trail, setTrail] = useState([])
  const parent = trail.at(-1)?.id
  const query = useQuery({
    queryKey: ['create-locations', parent],
    queryFn: ({ signal }) => api.locations(parent, signal),
  })
  return (
    <div className="mb-3">
      <h3 className="h6">Địa điểm</h3>
      <p aria-live="polite">Đã chọn: {value?.name ?? 'Chưa chọn'}</p>
      <Button
        variant="link"
        disabled={disabled || !value}
        onClick={() => onChange(null)}
      >
        Bỏ chọn địa điểm
      </Button>
      <p>{trail.map((p) => p.name).join(' / ') || 'Các cơ sở'}</p>
      {trail.length > 0 && (
        <Button
          variant="link"
          disabled={disabled}
          onClick={() => setTrail(trail.slice(0, -1))}
        >
          Quay lên
        </Button>
      )}
      {query.isPending ? (
        <p role="status">Đang tải địa điểm...</p>
      ) : query.isError ? (
        <Alert variant="danger">
          Không tải được địa điểm.{' '}
          <Button onClick={() => query.refetch()}>Thử lại</Button>
        </Alert>
      ) : (
        <ListGroup>
          {query.data
            ?.filter((x) => x.active)
            .map((x) => (
              <ListGroup.Item
                key={x.id}
                className="d-flex flex-wrap justify-content-between gap-2"
              >
                <span>
                  {x.name} ({x.code})
                </span>
                <span>
                  <Button
                    size="sm"
                    disabled={disabled}
                    onClick={() => onChange(x)}
                  >
                    Chọn {x.name}
                  </Button>
                  {!['ROOM', 'AREA'].includes(x.type) && (
                    <Button
                      variant="link"
                      size="sm"
                      disabled={disabled}
                      onClick={() => setTrail([...trail, x])}
                    >
                      Xem bên trong {x.name}
                    </Button>
                  )}
                </span>
              </ListGroup.Item>
            ))}
        </ListGroup>
      )}
      {query.data?.length === 0 && <p>Không có địa điểm con đang hoạt động.</p>}
    </div>
  )
}

export function CreateAssetSelector({
  locationId,
  value,
  onChange,
  disabled,
  api = createTicketApi,
}) {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const query = useQuery({
    queryKey: ['create-assets', locationId, search, page],
    queryFn: ({ signal }) => api.assets(locationId, search, page, signal),
    enabled: !!locationId,
  })
  return (
    <div className="mb-3">
      <Form.Label htmlFor="asset-search">Tìm tài sản</Form.Label>
      <Form.Control
        id="asset-search"
        disabled={disabled || !locationId}
        value={search}
        onChange={(e) => {
          setSearch(e.target.value)
          setPage(0)
        }}
      />
      <Form.Label htmlFor="create-asset">Tài sản hợp lệ</Form.Label>
      <Form.Select
        id="create-asset"
        disabled={disabled || !locationId || query.isPending || query.isError}
        value={value?.id ?? ''}
        onChange={(e) =>
          onChange(
            query.data?.content?.find((x) => String(x.id) === e.target.value) ??
              null,
          )
        }
      >
        <option value="">Chọn tài sản</option>
        {value && !query.data?.content?.some((x) => x.id === value.id) && (
          <option value={value.id}>
            {value.equipmentType?.name ?? value.assetCode} ({value.assetCode})
          </option>
        )}
        {query.data?.content?.map((x) => (
          <option value={x.id} key={x.id}>
            {x.equipmentType?.name ?? x.assetCode} ({x.assetCode})
          </option>
        ))}
      </Form.Select>
      {!locationId ? (
        <p>Chọn địa điểm trước khi chọn tài sản.</p>
      ) : query.isPending ? (
        <p role="status">Đang tải tài sản...</p>
      ) : query.isError ? (
        <Alert variant="danger">
          Không tải được tài sản.{' '}
          <Button onClick={() => query.refetch()}>Thử lại</Button>
        </Alert>
      ) : (
        query.data?.content?.length === 0 && (
          <p>Không có tài sản hợp lệ tại địa điểm này.</p>
        )
      )}
      {(query.data?.totalPages ?? 0) > 1 && (
        <div className="d-flex gap-2 mt-2">
          <Button
            disabled={disabled || page === 0}
            onClick={() => setPage(page - 1)}
          >
            Trang trước
          </Button>
          <span>
            Trang {page + 1}/{query.data.totalPages}
          </span>
          <Button
            disabled={disabled || page + 1 >= query.data.totalPages}
            onClick={() => setPage(page + 1)}
          >
            Trang sau
          </Button>
        </div>
      )}
    </div>
  )
}
