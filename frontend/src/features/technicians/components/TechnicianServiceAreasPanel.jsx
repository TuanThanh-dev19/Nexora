import { useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  ListGroup,
  Row,
  Spinner,
} from 'react-bootstrap'
import {
  FiChevronRight,
  FiCornerLeftUp,
  FiInfo,
  FiMapPin,
  FiPlus,
  FiSave,
  FiTrash2,
} from 'react-icons/fi'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { COVERAGE_HELPER_TEXT } from '../constants/technicianConstants'
import { formatTechnicianError, isConflictError } from '../api/technicianError'
import {
  useTechnicianLocations,
  useUpdateTechnicianServiceAreas,
} from '../hooks/useTechnicians'

export function TechnicianServiceAreasPanel({
  technician,
  readOnly = false,
  onConflict,
}) {
  const [workingAreas, setWorkingAreas] = useState(() =>
    (technician?.serviceAreas ?? []).map((a) => ({
      locationId: Number(a.locationId ?? a.id),
      locationCode: a.locationCode ?? a.code,
      locationName: a.locationName ?? a.name,
      type: a.type,
    })),
  )
  const [trail, setTrail] = useState([])
  const [feedback, setFeedback] = useState(null)

  const currentParentId = trail.at(-1)?.id ?? null
  const locationsQuery = useTechnicianLocations(currentParentId)
  const updateMutation = useUpdateTechnicianServiceAreas()

  const handleSelectArea = (location) => {
    if (workingAreas.some((a) => a.locationId === location.id)) return

    setWorkingAreas((prev) => [
      ...prev,
      {
        locationId: location.id,
        locationCode: location.code,
        locationName: location.name,
        type: location.type,
      },
    ])
  }

  const handleRemoveArea = (locationId) => {
    setWorkingAreas((prev) => prev.filter((a) => a.locationId !== locationId))
  }

  // Calculate dirty state
  const originalIds = (technician?.serviceAreas ?? [])
    .map((a) => Number(a.locationId ?? a.id))
    .sort()
  const currentIds = workingAreas.map((a) => Number(a.locationId)).sort()
  const isDirty =
    JSON.stringify(originalIds) !== JSON.stringify(currentIds)

  const handleSave = async () => {
    if (!isDirty || readOnly) return

    setFeedback(null)
    try {
      await updateMutation.mutateAsync({
        technicianId: technician.technicianId,
        payload: {
          locationIds: workingAreas.map((a) => a.locationId),
          version: technician.version,
        },
      })
      setFeedback({
        type: 'success',
        message: 'Đã cập nhật danh sách vùng phục vụ thành công.',
      })
    } catch (err) {
      if (isConflictError(err)) {
        if (onConflict) onConflict(err)
      } else {
        setFeedback({
          type: 'danger',
          message: formatTechnicianError(err),
        })
      }
    }
  }

  return (
    <Card className="shadow-sm mb-4">
      <Card.Header className="d-flex align-items-center justify-content-between py-3">
        <div className="d-flex align-items-center gap-2">
          <FiMapPin className="fs-5 text-primary" aria-hidden="true" />
          <h2 className="h5 mb-0">Vùng phục vụ (Service Areas)</h2>
        </div>
        <Badge bg="light" text="dark" className="border">
          {workingAreas.length} khu vực
        </Badge>
      </Card.Header>

      <Card.Body>
        <p className="text-secondary small mb-3">
          Cấu hình khu vực địa lý mà kỹ thuật viên chịu trách nhiệm xử lý sự cố.
        </p>

        {/* Mandatory Coverage Helper Callout */}
        <Alert variant="info" className="d-flex align-items-start gap-2 mb-3">
          <FiInfo className="fs-5 flex-shrink-0 mt-1" aria-hidden="true" />
          <div>
            <strong>Quy tắc bao hàm vùng phục vụ (Coverage):</strong>
            <p className="mb-0 mt-1 small">{COVERAGE_HELPER_TEXT}</p>
          </div>
        </Alert>

        {feedback && (
          <Alert
            variant={feedback.type}
            dismissible
            onClose={() => setFeedback(null)}
            className="mb-3"
          >
            {feedback.message}
          </Alert>
        )}

        <Row className="g-4">
          {/* Location Tree Selector (hidden in read-only) */}
          {!readOnly && (
            <Col xs={12} lg={6}>
              <div className="border rounded p-3 bg-light h-100">
                <h3 className="h6 mb-2">Chọn địa điểm phục vụ</h3>

                {/* Trail navigation */}
                <div className="d-flex align-items-center justify-content-between mb-2 flex-wrap gap-2">
                  <div className="small fw-semibold text-secondary">
                    Đang xem:{' '}
                    {trail.length === 0 ? (
                      <span className="text-dark">Toàn bộ cơ sở</span>
                    ) : (
                      <span>
                        {trail.map((p, idx) => (
                          <span key={p.id}>
                            {idx > 0 && ' / '}
                            <span className="text-dark">{p.name}</span>
                          </span>
                        ))}
                      </span>
                    )}
                  </div>
                  {trail.length > 0 && (
                    <Button
                      variant="link"
                      size="sm"
                      className="p-0 text-decoration-none d-inline-flex align-items-center gap-1"
                      onClick={() => setTrail((prev) => prev.slice(0, -1))}
                    >
                      <FiCornerLeftUp aria-hidden="true" /> Quay lên
                    </Button>
                  )}
                </div>

                {locationsQuery.isPending ? (
                  <LoadingSpinner label="Đang tải danh sách địa điểm..." />
                ) : locationsQuery.isError ? (
                  <Alert variant="warning" className="small">
                    Không thể tải địa điểm.{' '}
                    <Button
                      variant="link"
                      size="sm"
                      onClick={() => locationsQuery.refetch()}
                      className="p-0 text-decoration-underline"
                    >
                      Thử lại
                    </Button>
                  </Alert>
                ) : (
                  <ListGroup variant="flush" className="bg-white rounded border">
                    {locationsQuery.data?.length === 0 ? (
                      <ListGroup.Item className="text-secondary small text-center py-3">
                        Không có địa điểm con nào tại cấp này.
                      </ListGroup.Item>
                    ) : (
                      locationsQuery.data
                        ?.filter((loc) => loc.active)
                        .map((loc) => {
                          const isAlreadySelected = workingAreas.some(
                            (a) => a.locationId === loc.id,
                          )
                          const canHaveChildren = !['ROOM', 'AREA'].includes(
                            loc.type,
                          )

                          return (
                            <ListGroup.Item
                              key={loc.id}
                              className="d-flex align-items-center justify-content-between flex-wrap gap-2 py-2"
                            >
                              <div>
                                <span className="fw-semibold me-2">{loc.name}</span>
                                <code className="text-secondary small me-2">
                                  {loc.code}
                                </code>
                                {loc.type && (
                                  <Badge bg="light" text="dark" className="border small">
                                    {loc.type}
                                  </Badge>
                                )}
                              </div>
                              <div className="d-flex align-items-center gap-2">
                                <Button
                                  size="sm"
                                  variant={
                                    isAlreadySelected
                                      ? 'secondary'
                                      : 'outline-primary'
                                  }
                                  disabled={
                                    isAlreadySelected || updateMutation.isPending
                                  }
                                  onClick={() => handleSelectArea(loc)}
                                  className="d-inline-flex align-items-center gap-1"
                                >
                                  {isAlreadySelected ? (
                                    'Đã chọn'
                                  ) : (
                                    <>
                                      <FiPlus aria-hidden="true" /> Chọn
                                    </>
                                  )}
                                </Button>
                                {canHaveChildren && (
                                  <Button
                                    size="sm"
                                    variant="link"
                                    className="p-0 text-decoration-none d-inline-flex align-items-center"
                                    onClick={() =>
                                      setTrail((prev) => [...prev, loc])
                                    }
                                    title={`Xem các địa điểm bên trong ${loc.name}`}
                                  >
                                    Xem <FiChevronRight aria-hidden="true" />
                                  </Button>
                                )}
                              </div>
                            </ListGroup.Item>
                          )
                        })
                    )}
                  </ListGroup>
                )}
              </div>
            </Col>
          )}

          {/* Selected Service Areas List */}
          <Col xs={12} lg={readOnly ? 12 : 6}>
            <div className="border rounded p-3 h-100 bg-white">
              <h3 className="h6 mb-3">Các khu vực phụ trách hiện tại</h3>

              {workingAreas.length === 0 ? (
                <EmptyState message="Chưa cấu hình vùng phục vụ nào cho kỹ thuật viên này." />
              ) : (
                <ListGroup variant="flush">
                  {workingAreas.map((area) => (
                    <ListGroup.Item
                      key={area.locationId}
                      className="d-flex align-items-center justify-content-between py-2 px-2 border-bottom"
                    >
                      <div className="d-flex align-items-center gap-2">
                        <FiMapPin className="text-primary flex-shrink-0" aria-hidden="true" />
                        <div>
                          <strong className="d-block">{area.locationName}</strong>
                          <small className="text-secondary">
                            Mã: {area.locationCode}
                          </small>
                        </div>
                      </div>

                      {!readOnly && (
                        <Button
                          size="sm"
                          variant="outline-danger"
                          aria-label={`Bỏ chọn vùng ${area.locationName}`}
                          onClick={() => handleRemoveArea(area.locationId)}
                          disabled={updateMutation.isPending}
                        >
                          <FiTrash2 aria-hidden="true" /> Bỏ chọn
                        </Button>
                      )}
                    </ListGroup.Item>
                  ))}
                </ListGroup>
              )}
            </div>
          </Col>
        </Row>

        {!readOnly && (
          <div className="d-flex justify-content-end mt-4 pt-3 border-top">
            <Button
              variant="primary"
              onClick={handleSave}
              disabled={!isDirty || updateMutation.isPending}
              className="d-inline-flex align-items-center gap-1"
            >
              {updateMutation.isPending ? (
                <>
                  <Spinner size="sm" animation="border" /> Đang lưu...
                </>
              ) : (
                <>
                  <FiSave aria-hidden="true" /> Lưu vùng phục vụ
                </>
              )}
            </Button>
          </div>
        )}
      </Card.Body>
    </Card>
  )
}
