import { useId, useState } from 'react'
import { Button, Col, Form, Row, Spinner } from 'react-bootstrap'
import { FiAlertCircle, FiMapPin, FiRefreshCw, FiX } from 'react-icons/fi'
import { locationApi } from '../api/locationApi'
import { useLocation, useLocations } from '../hooks/useLocations'

/**
 * Reusable Location Selector supporting:
 * - Cascading Selects: Campus -> Building -> Floor -> Room / Area
 * - Lazy load per level by parentId and active=true
 * - Leaf Mode (for tickets/assets) vs Node Mode (for service areas/admin)
 * - Cascade Reset when higher level changes
 * - Per-level loading and error states with Retry button
 * - Form validation feedback, clear button, and breadcrumb path
 */
export function LocationSelector({
  value,
  onChange,
  mode = 'leaf', // 'leaf' | 'node'
  disabled = false,
  required = false,
  isInvalid = false,
  error = '',
  label = 'Địa điểm',
  helperText = '',
  idPrefix: customIdPrefix,
  onReset,
  api: _api = locationApi,
}) {
  const generatedId = useId().replace(/:/g, '')
  const idPrefix = customIdPrefix || `loc-sel-${generatedId}`

  // Track selection at each level: { id, code, name, type, parentId }
  const [campus, setCampus] = useState(null)
  const [building, setBuilding] = useState(null)
  const [floor, setFloor] = useState(null)
  const [room, setRoom] = useState(null)

  // Value resolution: if value is an ID or object, fetch details if needed
  const selectedId = typeof value === 'object' ? value?.id : value
  const detailQuery = useLocation(selectedId)

  // Adjust state during render when selectedId changes externally
  const [prevSelectedId, setPrevSelectedId] = useState(selectedId)
  if (selectedId !== prevSelectedId) {
    setPrevSelectedId(selectedId)
    if (!selectedId) {
      setCampus(null)
      setBuilding(null)
      setFloor(null)
      setRoom(null)
    }
  }

  const initialDetail =
    detailQuery.data || (typeof value === 'object' ? value : null)

  // Level 1: Campuses (parentId = null)
  const campusesQuery = useLocations(null, {
    active: true,
    enabled: true,
  })

  // Level 2: Buildings (parentId = campus.id)
  const buildingsQuery = useLocations(campus?.id, {
    active: true,
    enabled: Boolean(campus?.id),
  })

  // Level 3: Floors (parentId = building.id)
  const floorsQuery = useLocations(building?.id, {
    active: true,
    enabled: Boolean(building?.id),
  })

  // Level 4: Rooms / Areas (parentId = floor.id)
  const roomsQuery = useLocations(floor?.id, {
    active: true,
    enabled: Boolean(floor?.id),
  })

  // Cascade handlers
  const handleCampusChange = (e) => {
    const nextId = e.target.value
    if (!nextId) {
      setCampus(null)
      setBuilding(null)
      setFloor(null)
      setRoom(null)
      onChange?.(null)
      return
    }

    const selected =
      campusesQuery.data?.find((c) => String(c.id) === nextId) || null
    setCampus(selected)
    setBuilding(null)
    setFloor(null)
    setRoom(null)

    if (mode === 'node') {
      onChange?.(selected)
    } else {
      // Leaf mode: resetting parent clears leaf selection
      onChange?.(null)
    }
  }

  const handleBuildingChange = (e) => {
    const nextId = e.target.value
    if (!nextId) {
      setBuilding(null)
      setFloor(null)
      setRoom(null)
      if (mode === 'node') {
        onChange?.(campus)
      } else {
        onChange?.(null)
      }
      return
    }

    const selected =
      buildingsQuery.data?.find((b) => String(b.id) === nextId) || null
    setBuilding(selected)
    setFloor(null)
    setRoom(null)

    if (mode === 'node') {
      onChange?.(selected)
    } else {
      onChange?.(null)
    }
  }

  const handleFloorChange = (e) => {
    const nextId = e.target.value
    if (!nextId) {
      setFloor(null)
      setRoom(null)
      if (mode === 'node') {
        onChange?.(building || campus)
      } else {
        onChange?.(null)
      }
      return
    }

    const selected =
      floorsQuery.data?.find((f) => String(f.id) === nextId) || null
    setFloor(selected)
    setRoom(null)

    if (mode === 'node') {
      onChange?.(selected)
    } else {
      onChange?.(null)
    }
  }

  const handleRoomChange = (e) => {
    const nextId = e.target.value
    if (!nextId) {
      setRoom(null)
      if (mode === 'node') {
        onChange?.(floor || building || campus)
      } else {
        onChange?.(null)
      }
      return
    }

    const selected =
      roomsQuery.data?.find((r) => String(r.id) === nextId) || null
    setRoom(selected)
    onChange?.(selected)
  }

  const handleClear = () => {
    setCampus(null)
    setBuilding(null)
    setFloor(null)
    setRoom(null)
    onChange?.(null)
    onReset?.()
  }

  // Determine current active selection
  const currentSelection = room || floor || building || campus || initialDetail
  const isLeafSelected =
    Boolean(room) ||
    (floor && roomsQuery.isSuccess && roomsQuery.data?.length === 0) ||
    (building && floorsQuery.isSuccess && floorsQuery.data?.length === 0) ||
    (campus && buildingsQuery.isSuccess && buildingsQuery.data?.length === 0) ||
    Boolean(initialDetail && ['ROOM', 'AREA'].includes(initialDetail.type))

  // Construct breadcrumbs
  const selectedLevels = [campus, building, floor, room].filter(Boolean)
  const breadcrumbItems =
    selectedLevels.length > 0
      ? selectedLevels
      : initialDetail
        ? [initialDetail]
        : []

  return (
    <div className="location-selector mb-3" data-testid="location-selector">
      {/* Header with Label, Clear button, and Mode indicator */}
      <div className="d-flex align-items-center justify-content-between mb-2">
        <Form.Label className="mb-0 fw-semibold text-dark">
          <FiMapPin className="text-primary me-1" aria-hidden="true" />
          {label} {required && <span className="text-danger">*</span>}
        </Form.Label>

        {currentSelection && !disabled && (
          <Button
            variant="link"
            size="sm"
            className="p-0 text-decoration-none text-danger d-inline-flex align-items-center gap-1"
            onClick={handleClear}
            aria-label="Xóa lựa chọn địa điểm"
          >
            <FiX aria-hidden="true" /> Xóa lựa chọn
          </Button>
        )}
      </div>

      {helperText && (
        <Form.Text className="text-secondary d-block mb-2">
          {helperText}
        </Form.Text>
      )}

      {/* Breadcrumb Path Preview */}
      {breadcrumbItems.length > 0 && (
        <div
          className="location-breadcrumb bg-light p-2 rounded border mb-3"
          aria-label="Đường dẫn địa điểm đã chọn"
        >
          <span className="small text-secondary me-1">Đã chọn:</span>
          {breadcrumbItems.map((item, idx) => (
            <span key={item.id} className="d-inline-flex align-items-center">
              {idx > 0 && <span className="breadcrumb-sep mx-1">›</span>}
              <span
                className={
                  idx === breadcrumbItems.length - 1
                    ? 'breadcrumb-current fw-bold text-dark'
                    : 'text-secondary'
                }
              >
                {item.name}
              </span>
            </span>
          ))}
          {mode === 'leaf' && !isLeafSelected && (
            <span className="badge bg-warning text-dark ms-2 small">
              Vui lòng chọn tiếp đến cấp phòng/khu vực
            </span>
          )}
        </div>
      )}

      {/* Cascading Selects Grid */}
      <Row className="g-2">
        {/* Level 1: Campus */}
        <Col xs={12} sm={6} lg={3}>
          <Form.Group controlId={`${idPrefix}-campus`}>
            <Form.Label className="small text-secondary mb-1">
              1. Cơ sở (Campus)
            </Form.Label>
            <div className="position-relative">
              <Form.Select
                value={campus?.id ?? ''}
                onChange={handleCampusChange}
                disabled={disabled || campusesQuery.isLoading}
                isInvalid={isInvalid}
                aria-label="Chọn cơ sở"
              >
                <option value="">-- Chọn cơ sở --</option>
                {campusesQuery.data?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.code})
                  </option>
                ))}
              </Form.Select>
              {campusesQuery.isLoading && (
                <Spinner
                  animation="border"
                  size="sm"
                  className="position-absolute end-0 top-50 translate-middle-y me-4 text-primary"
                  role="status"
                  aria-label="Đang tải cơ sở"
                />
              )}
            </div>
            {campusesQuery.isError && (
              <div className="d-flex align-items-center justify-content-between mt-1 text-danger small">
                <span>Lỗi tải cơ sở</span>
                <Button
                  variant="link"
                  size="sm"
                  className="p-0 text-danger"
                  onClick={() => campusesQuery.refetch()}
                >
                  <FiRefreshCw className="me-1" /> Thử lại
                </Button>
              </div>
            )}
          </Form.Group>
        </Col>

        {/* Level 2: Building */}
        <Col xs={12} sm={6} lg={3}>
          <Form.Group controlId={`${idPrefix}-building`}>
            <Form.Label className="small text-secondary mb-1">
              2. Tòa nhà (Building)
            </Form.Label>
            <div className="position-relative">
              <Form.Select
                value={building?.id ?? ''}
                onChange={handleBuildingChange}
                disabled={disabled || !campus || buildingsQuery.isLoading}
                isInvalid={isInvalid}
                aria-label="Chọn tòa nhà"
              >
                <option value="">
                  {!campus ? '-- Chọn cơ sở trước --' : '-- Chọn tòa nhà --'}
                </option>
                {buildingsQuery.data?.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name} ({b.code})
                  </option>
                ))}
              </Form.Select>
              {buildingsQuery.isLoading && (
                <Spinner
                  animation="border"
                  size="sm"
                  className="position-absolute end-0 top-50 translate-middle-y me-4 text-primary"
                  role="status"
                  aria-label="Đang tải tòa nhà"
                />
              )}
            </div>
            {buildingsQuery.isError && (
              <div className="d-flex align-items-center justify-content-between mt-1 text-danger small">
                <span>Lỗi tải tòa nhà</span>
                <Button
                  variant="link"
                  size="sm"
                  className="p-0 text-danger"
                  onClick={() => buildingsQuery.refetch()}
                >
                  <FiRefreshCw className="me-1" /> Thử lại
                </Button>
              </div>
            )}
          </Form.Group>
        </Col>

        {/* Level 3: Floor */}
        <Col xs={12} sm={6} lg={3}>
          <Form.Group controlId={`${idPrefix}-floor`}>
            <Form.Label className="small text-secondary mb-1">
              3. Tầng (Floor)
            </Form.Label>
            <div className="position-relative">
              <Form.Select
                value={floor?.id ?? ''}
                onChange={handleFloorChange}
                disabled={disabled || !building || floorsQuery.isLoading}
                isInvalid={isInvalid}
                aria-label="Chọn tầng"
              >
                <option value="">
                  {!building ? '-- Chọn tòa nhà trước --' : '-- Chọn tầng --'}
                </option>
                {floorsQuery.data?.map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.name} ({f.code})
                  </option>
                ))}
              </Form.Select>
              {floorsQuery.isLoading && (
                <Spinner
                  animation="border"
                  size="sm"
                  className="position-absolute end-0 top-50 translate-middle-y me-4 text-primary"
                  role="status"
                  aria-label="Đang tải tầng"
                />
              )}
            </div>
            {floorsQuery.isError && (
              <div className="d-flex align-items-center justify-content-between mt-1 text-danger small">
                <span>Lỗi tải tầng</span>
                <Button
                  variant="link"
                  size="sm"
                  className="p-0 text-danger"
                  onClick={() => floorsQuery.refetch()}
                >
                  <FiRefreshCw className="me-1" /> Thử lại
                </Button>
              </div>
            )}
          </Form.Group>
        </Col>

        {/* Level 4: Room / Area */}
        <Col xs={12} sm={6} lg={3}>
          <Form.Group controlId={`${idPrefix}-room`}>
            <Form.Label className="small text-secondary mb-1">
              4. Phòng / Khu vực (Room / Area)
            </Form.Label>
            <div className="position-relative">
              <Form.Select
                value={room?.id ?? ''}
                onChange={handleRoomChange}
                disabled={disabled || !floor || roomsQuery.isLoading}
                isInvalid={isInvalid}
                aria-label="Chọn phòng hoặc khu vực"
              >
                <option value="">
                  {!floor
                    ? '-- Chọn tầng trước --'
                    : roomsQuery.data?.length === 0
                      ? '-- Không có phòng con --'
                      : '-- Chọn phòng/khu vực --'}
                </option>
                {roomsQuery.data?.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.name} ({r.code})
                  </option>
                ))}
              </Form.Select>
              {roomsQuery.isLoading && (
                <Spinner
                  animation="border"
                  size="sm"
                  className="position-absolute end-0 top-50 translate-middle-y me-4 text-primary"
                  role="status"
                  aria-label="Đang tải phòng hoặc khu vực"
                />
              )}
            </div>
            {roomsQuery.isError && (
              <div className="d-flex align-items-center justify-content-between mt-1 text-danger small">
                <span>Lỗi tải phòng</span>
                <Button
                  variant="link"
                  size="sm"
                  className="p-0 text-danger"
                  onClick={() => roomsQuery.refetch()}
                >
                  <FiRefreshCw className="me-1" /> Thử lại
                </Button>
              </div>
            )}
          </Form.Group>
        </Col>
      </Row>

      {/* Mode helper / Node Mode explicit confirmation */}
      {mode === 'node' && currentSelection && (
        <div className="mt-2 text-end">
          <small className="text-secondary me-2">
            Chế độ Node: Đang chọn <strong>{currentSelection.name}</strong> (
            {currentSelection.type})
          </small>
        </div>
      )}

      {/* Field Validation Error Feedback */}
      {error && (
        <Form.Control.Feedback type="invalid" className="d-block mt-2">
          <FiAlertCircle className="me-1" aria-hidden="true" />
          {error}
        </Form.Control.Feedback>
      )}
    </div>
  )
}
