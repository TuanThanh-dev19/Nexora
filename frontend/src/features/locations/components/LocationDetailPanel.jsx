import {
  FiCheckCircle,
  FiChevronRight,
  FiEdit,
  FiInfo,
  FiPlus,
  FiSlash,
} from 'react-icons/fi'
import { Alert, Badge, Button, Card, Col, Row, Table } from 'react-bootstrap'
import {
  LOCATION_TYPE_CODES,
  LOCATION_TYPE_LABELS,
} from '../constants/locationTypes'
import { useLocation, useLocations } from '../hooks/useLocations'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'

const CAN_HAVE_CHILDREN = [
  LOCATION_TYPE_CODES.CAMPUS,
  LOCATION_TYPE_CODES.BUILDING,
  LOCATION_TYPE_CODES.FLOOR,
]

export function LocationDetailPanel({
  location,
  breadcrumbs = [],
  onSelectLocation,
  onOpenEdit,
  onOpenDeactivate,
  onOpenCreateChild,
  activeFilter,
}) {
  // Query direct children for the children list in detail panel
  const childrenQuery = useLocations(location?.id, {
    active: activeFilter,
    enabled: Boolean(location?.id),
  })

  // Query parent location if parentId exists and not already loaded
  const parentQuery = useLocation(location?.parentId)

  if (!location) {
    return (
      <Card className="shadow-sm">
        <Card.Body className="p-5 text-center text-secondary">
          <FiInfo size={40} className="mb-3 text-muted" aria-hidden="true" />
          <h3 className="h5 mb-2">Chưa chọn địa điểm</h3>
          <p className="mb-0">
            Vui lòng chọn một địa điểm từ cây bên trái để xem thông tin chi tiết,
            quản lý địa điểm con hoặc thực hiện chỉnh sửa.
          </p>
        </Card.Body>
      </Card>
    )
  }

  const typeLabel = LOCATION_TYPE_LABELS[location.type] ?? location.type
  const canHaveChildren = CAN_HAVE_CHILDREN.includes(location.type)

  return (
    <Card className="shadow-sm location-detail-card">
      <Card.Body>
        {/* Breadcrumb path */}
        {breadcrumbs.length > 0 && (
          <nav aria-label="Đường dẫn phân cấp địa điểm" className="location-breadcrumb">
            {breadcrumbs.map((crumb, idx) => {
              const isLast = idx === breadcrumbs.length - 1
              return (
                <span key={crumb.id} className="d-inline-flex align-items-center gap-1">
                  {isLast ? (
                    <span className="breadcrumb-current" aria-current="page">
                      {crumb.name}
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="breadcrumb-item-btn"
                      onClick={() => onSelectLocation(crumb)}
                    >
                      {crumb.name}
                    </button>
                  )}
                  {!isLast && <FiChevronRight className="breadcrumb-sep" aria-hidden="true" />}
                </span>
              )
            })}
          </nav>
        )}

        {/* Heading & Badges */}
        <div className="d-flex align-items-start justify-content-between flex-wrap gap-2 mb-3 pb-3 border-bottom">
          <div>
            <div className="d-flex align-items-center gap-2 flex-wrap mb-1">
              <h2 className="h4 mb-0 text-dark">{location.name}</h2>
              <Badge bg="light" text="dark" className="border font-monospace">
                {location.code}
              </Badge>
              <Badge bg="primary" className="location-type-badge">
                {typeLabel}
              </Badge>
              <Badge
                bg={location.active ? 'success' : 'secondary'}
                className="location-type-badge"
              >
                {location.active ? 'Hoạt động' : 'Ngừng hoạt động'}
              </Badge>
            </div>
            {location.description && (
              <p className="text-secondary mb-0 mt-2">{location.description}</p>
            )}
          </div>

          {/* Action Buttons */}
          <div className="d-flex align-items-center gap-2 flex-wrap">
            {canHaveChildren && (
              <Button
                size="sm"
                variant="outline-primary"
                onClick={() => onOpenCreateChild(location)}
              >
                <FiPlus aria-hidden="true" /> Thêm địa điểm con
              </Button>
            )}
            <Button
              size="sm"
              variant="outline-secondary"
              onClick={() => onOpenEdit(location)}
            >
              <FiEdit aria-hidden="true" /> Chỉnh sửa
            </Button>
            {location.active ? (
              <Button
                size="sm"
                variant="outline-danger"
                onClick={() => onOpenDeactivate(location, false)}
              >
                <FiSlash aria-hidden="true" /> Ngừng hoạt động
              </Button>
            ) : (
              <Button
                size="sm"
                variant="outline-success"
                onClick={() => onOpenDeactivate(location, true)}
              >
                <FiCheckCircle aria-hidden="true" /> Kích hoạt lại
              </Button>
            )}
          </div>
        </div>

        {/* Metadata Details */}
        <Row className="g-3 mb-4">
          <Col sm={6}>
            <div className="p-3 bg-light rounded border h-100">
              <span className="text-secondary small d-block mb-1">Địa điểm cha</span>
              {location.parentId == null ? (
                <span className="fw-semibold text-dark">
                  Địa điểm gốc (Cơ sở — không có cha)
                </span>
              ) : parentQuery.isPending ? (
                <span className="text-secondary small">Đang tải địa điểm cha...</span>
              ) : parentQuery.data ? (
                <div className="d-flex align-items-center justify-content-between gap-2">
                  <div>
                    <strong>{parentQuery.data.name}</strong>{' '}
                    <code className="small">({parentQuery.data.code})</code>
                    <div className="small text-secondary">
                      {LOCATION_TYPE_LABELS[parentQuery.data.type]}
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="link"
                    className="p-0 text-decoration-none"
                    onClick={() => onSelectLocation(parentQuery.data)}
                  >
                    Xem cha
                  </Button>
                </div>
              ) : (
                <span className="text-muted small">Không rõ thông tin</span>
              )}
            </div>
          </Col>

          <Col sm={6}>
            <div className="p-3 bg-light rounded border h-100">
              <span className="text-secondary small d-block mb-1">Cấp độ phân cấp</span>
              <span className="fw-semibold text-dark">
                {typeLabel} ({location.type})
              </span>
              <div className="small text-secondary mt-1">
                {canHaveChildren
                  ? 'Có thể chứa các địa điểm con'
                  : 'Cấp phòng/khu vực (nút lá)'}
              </div>
            </div>
          </Col>
        </Row>

        {/* Direct Children Section */}
        {canHaveChildren && (
          <div className="mt-4 pt-3 border-top">
            <div className="d-flex align-items-center justify-content-between mb-3">
              <h3 className="h6 mb-0">
                Địa điểm con trực thuộc{' '}
                {childrenQuery.data && (
                  <Badge bg="secondary" pill className="ms-1">
                    {childrenQuery.data.length}
                  </Badge>
                )}
              </h3>
              <Button
                size="sm"
                variant="outline-primary"
                onClick={() => onOpenCreateChild(location)}
              >
                <FiPlus aria-hidden="true" /> Thêm địa điểm con
              </Button>
            </div>

            {childrenQuery.isPending ? (
              <LoadingSpinner label="Đang tải danh sách địa điểm con..." />
            ) : childrenQuery.isError ? (
              <Alert variant="warning" className="small">
                Không thể tải danh sách địa điểm con.{' '}
                <Button
                  size="sm"
                  variant="link"
                  className="p-0"
                  onClick={() => childrenQuery.refetch()}
                >
                  Thử lại
                </Button>
              </Alert>
            ) : childrenQuery.data?.length === 0 ? (
              <p className="text-secondary small fst-italic mb-0">
                Chưa có địa điểm con nào thuộc {location.name}.
              </p>
            ) : (
              <Table responsive hover className="align-middle mb-0 small">
                <thead>
                  <tr>
                    <th>Tên địa điểm</th>
                    <th>Mã</th>
                    <th>Loại</th>
                    <th>Trạng thái</th>
                    <th className="text-end">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {childrenQuery.data?.map((child) => (
                    <tr key={child.id}>
                      <td>
                        <strong>{child.name}</strong>
                        {child.description && (
                          <div className="text-secondary small text-truncate" style={{ maxWidth: '240px' }}>
                            {child.description}
                          </div>
                        )}
                      </td>
                      <td>
                        <code>{child.code}</code>
                      </td>
                      <td>
                        <Badge bg="light" text="dark" className="border">
                          {LOCATION_TYPE_LABELS[child.type] ?? child.type}
                        </Badge>
                      </td>
                      <td>
                        <Badge bg={child.active ? 'success' : 'secondary'}>
                          {child.active ? 'Hoạt động' : 'Ngừng hoạt động'}
                        </Badge>
                      </td>
                      <td className="text-end">
                        <Button
                          size="sm"
                          variant="outline-primary"
                          onClick={() => onSelectLocation(child)}
                        >
                          Xem chi tiết
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </div>
        )}
      </Card.Body>
    </Card>
  )
}
