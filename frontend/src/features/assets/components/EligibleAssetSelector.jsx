import { useEffect, useId, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Form,
  InputGroup,
  ListGroup,
  Row,
  Stack,
} from 'react-bootstrap'
import {
  FiAlertTriangle,
  FiBox,
  FiCheck,
  FiEdit2,
  FiInfo,
  FiRefreshCw,
  FiSearch,
  FiTrash2,
  FiX,
} from 'react-icons/fi'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { useEquipmentTypes } from '../../equipmentTypes/hooks/useEquipmentTypes'
import { useEligibleAssets } from '../hooks/useEligibleAssets'
import {
  ASSET_POLICIES,
  isAssetEligible,
  resolveAssetPolicy,
} from '../utils/assetPolicy'
import { AssetStatusBadge } from './AssetStatusBadge'

/**
 * Reusable Eligible Asset Selector Component
 * Adheres strictly to Issue #47 & Issue #38 contract:
 * - Only queries assets for the exact locationId via GET /api/v1/locations/{locationId}/eligible-assets
 * - Policy support: REQUIRED, OPTIONAL, FORBIDDEN (+ requiresEquipment)
 * - Equipment Type filter dropdown (GET /api/v1/equipment-types?active=true)
 * - Debounced search query
 * - Pagination controls
 * - Selected Asset Summary card
 * - Stale Reference / Invalidated Asset detection & warning
 * - Keyboard accessible, loading states, retry on error
 */
export function EligibleAssetSelector({
  locationId,
  locationName = '',
  value = null,
  onChange,
  policy: rawPolicy,
  assetPolicy,
  requiresEquipment = false,
  disabled = false,
  isInvalid = false,
  error = '',
  label = 'Thiết bị liên quan',
  idPrefix: customIdPrefix,
  pageSize = 10,
  onStale,
}) {
  const generatedId = useId().replace(/:/g, '')
  const idPrefix = customIdPrefix || `asset-sel-${generatedId}`

  // Resolve business policy
  const effectivePolicy = resolveAssetPolicy({
    policy: rawPolicy,
    assetPolicy,
    requiresEquipment,
  })
  const isForbidden = effectivePolicy === ASSET_POLICIES.FORBIDDEN
  const isRequired = effectivePolicy === ASSET_POLICIES.REQUIRED

  // Filter & Search states
  const [equipmentTypeId, setEquipmentTypeId] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [page, setPage] = useState(0)
  const [isChangingAsset, setIsChangingAsset] = useState(false)

  // Debounce search query (300ms)
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(searchInput.trim())
      setPage(0)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchInput])

  // Reset page when equipmentTypeId changes
  const handleEquipmentTypeChange = (e) => {
    setEquipmentTypeId(e.target.value)
    setPage(0)
  }

  // Handle FORBIDDEN policy: auto-clear selected asset if present
  useEffect(() => {
    if (isForbidden && value) {
      onChange?.(null)
    }
  }, [isForbidden, value, onChange])

  // Track previous locationId to auto-clear asset when location changes during render
  const [prevLocationId, setPrevLocationId] = useState(locationId)
  if (locationId !== prevLocationId) {
    setPrevLocationId(locationId)
    setPage(0)
    setSearchInput('')
    setDebouncedQuery('')
    setEquipmentTypeId('')
    setIsChangingAsset(false)

    // If location changed and an asset was selected, clear it
    if (value) {
      onChange?.(null)
    }
  }

  // Detect Stale Reference:
  // If an asset is selected, verify it belongs to current location and has ACTIVE/UNDER_MAINTENANCE status
  const isStale = Boolean(
    value && locationId && !isAssetEligible(value, locationId),
  )

  useEffect(() => {
    if (isStale && value) {
      onStale?.(value)
    }
  }, [isStale, value, onStale])

  // Equipment Types Query (active=true)
  const equipmentTypesQuery = useEquipmentTypes(
    true,
    Boolean(locationId) && !isForbidden,
  )

  // Eligible Assets Query
  const eligibleAssetsQuery = useEligibleAssets(locationId, {
    page,
    size: pageSize,
    equipmentTypeId: equipmentTypeId || undefined,
    query: debouncedQuery || undefined,
    enabled: Boolean(locationId) && !isForbidden && (!value || isChangingAsset),
  })

  // Select asset handler
  const handleSelectAsset = (asset) => {
    onChange?.(asset)
    setIsChangingAsset(false)
  }

  // Deselect / Clear handler
  const handleDeselect = () => {
    onChange?.(null)
    setIsChangingAsset(false)
  }

  // 1. FORBIDDEN Policy State
  if (isForbidden) {
    return (
      <div
        className="eligible-asset-selector mb-3"
        data-testid="eligible-asset-selector"
      >
        <Form.Label className="fw-semibold text-dark mb-1">
          <FiBox className="text-secondary me-1" aria-hidden="true" />
          {label}
        </Form.Label>
        <Alert
          variant="light"
          className="border small text-secondary py-2 px-3 mb-0"
        >
          <FiInfo className="me-2 text-primary" aria-hidden="true" />
          Danh mục sự cố này không yêu cầu và không áp dụng chọn thiết bị.
        </Alert>
      </div>
    )
  }

  // 2. Missing Location Hint State
  if (!locationId) {
    return (
      <div
        className="eligible-asset-selector mb-3"
        data-testid="eligible-asset-selector"
      >
        <div className="d-flex align-items-center justify-content-between mb-1">
          <Form.Label className="fw-semibold text-dark mb-0">
            <FiBox className="text-secondary me-1" aria-hidden="true" />
            {label} {isRequired && <span className="text-danger">*</span>}
            {!isRequired && (
              <span className="text-secondary small ms-1">(Tùy chọn)</span>
            )}
          </Form.Label>
        </div>
        <Alert
          variant="light"
          className="border border-warning-subtle bg-warning-subtle bg-opacity-25 py-2 px-3 mb-0 small text-secondary"
        >
          <FiInfo className="text-warning-emphasis me-2" aria-hidden="true" />
          <strong>Vui lòng chọn địa điểm trước khi chọn thiết bị.</strong> Danh
          sách thiết bị hợp lệ sẽ tự động tải theo đúng địa điểm đã chọn.
        </Alert>
        {error && (
          <Form.Control.Feedback type="invalid" className="d-block mt-1">
            {error}
          </Form.Control.Feedback>
        )}
      </div>
    )
  }

  // 3. Stale Reference State Warning
  const renderStaleAlert = () => (
    <Alert variant="danger" className="d-flex align-items-start gap-2 mb-3">
      <FiAlertTriangle className="fs-5 flex-shrink-0 mt-1" aria-hidden="true" />
      <div className="flex-grow-1">
        <strong>Thiết bị không còn hợp lệ:</strong>
        <p className="mb-1 small">
          Thiết bị <strong>{value?.assetCode}</strong> không còn thuộc địa điểm{' '}
          <strong>{locationName || `ID ${locationId}`}</strong> hoặc đã ngừng
          hoạt động (trạng thái: {value?.status}).
        </p>
        <div className="d-flex gap-2 mt-2">
          <Button
            size="sm"
            variant="danger"
            onClick={() => {
              handleDeselect()
              setIsChangingAsset(true)
            }}
          >
            Chọn thiết bị khác
          </Button>
          <Button
            size="sm"
            variant="outline-secondary"
            onClick={handleDeselect}
          >
            Bỏ chọn
          </Button>
        </div>
      </div>
    </Alert>
  )

  // 4. Selected Asset Summary View (when not actively re-selecting)
  if (value && !isChangingAsset) {
    return (
      <div
        className="eligible-asset-selector mb-3"
        data-testid="eligible-asset-selector"
      >
        <div className="d-flex align-items-center justify-content-between mb-2">
          <Form.Label className="fw-semibold text-dark mb-0">
            <FiBox className="text-primary me-1" aria-hidden="true" />
            {label} {isRequired && <span className="text-danger">*</span>}
          </Form.Label>
        </div>

        {isStale && renderStaleAlert()}

        <Card
          className={`border ${isStale ? 'border-danger' : 'border-success-subtle'} bg-light shadow-sm`}
        >
          <Card.Body className="p-3">
            <div className="d-flex align-items-start justify-content-between flex-wrap gap-2">
              <div className="d-flex align-items-start gap-3">
                <div className="rounded-circle bg-white p-2 border shadow-sm text-primary d-flex align-items-center justify-content-center">
                  <FiBox className="fs-4" aria-hidden="true" />
                </div>
                <div>
                  <div className="d-flex align-items-center gap-2 flex-wrap">
                    <strong className="fs-6 text-dark">
                      {value.assetCode}
                    </strong>
                    {value.equipmentType?.name && (
                      <Badge bg="secondary" className="small">
                        {value.equipmentType.name}
                      </Badge>
                    )}
                    <AssetStatusBadge status={value.status} />
                  </div>
                  {value.description && (
                    <p className="small text-secondary mb-1 mt-1">
                      {value.description}
                    </p>
                  )}
                  {value.location?.name && (
                    <small className="text-muted d-block">
                      Địa điểm: <strong>{value.location.name}</strong> (
                      {value.location.code})
                    </small>
                  )}
                </div>
              </div>

              {!disabled && (
                <Stack direction="horizontal" gap={2}>
                  <Button
                    size="sm"
                    variant="outline-primary"
                    onClick={() => setIsChangingAsset(true)}
                    className="d-inline-flex align-items-center gap-1"
                  >
                    <FiEdit2 aria-hidden="true" /> Đổi thiết bị
                  </Button>
                  {!isRequired && (
                    <Button
                      size="sm"
                      variant="outline-danger"
                      onClick={handleDeselect}
                      className="d-inline-flex align-items-center gap-1"
                    >
                      <FiTrash2 aria-hidden="true" /> Bỏ chọn
                    </Button>
                  )}
                </Stack>
              )}
            </div>
          </Card.Body>
        </Card>

        {error && (
          <Form.Control.Feedback type="invalid" className="d-block mt-1">
            {error}
          </Form.Control.Feedback>
        )}
      </div>
    )
  }

  // 5. Asset Search, Filter, and Selection List View
  const pageData = eligibleAssetsQuery.data
  const items = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  return (
    <div
      className="eligible-asset-selector mb-3"
      data-testid="eligible-asset-selector"
    >
      {/* Header with Label and Cancel action if re-selecting */}
      <div className="d-flex align-items-center justify-content-between mb-2">
        <Form.Label className="fw-semibold text-dark mb-0">
          <FiBox className="text-primary me-1" aria-hidden="true" />
          {label} {isRequired && <span className="text-danger">*</span>}
          {!isRequired && (
            <span className="text-secondary small ms-1">(Tùy chọn)</span>
          )}
        </Form.Label>

        {isChangingAsset && value && (
          <Button
            size="sm"
            variant="link"
            className="p-0 text-secondary text-decoration-none"
            onClick={() => setIsChangingAsset(false)}
          >
            <FiX aria-hidden="true" /> Giữ thiết bị đã chọn
          </Button>
        )}
      </div>

      {isStale && renderStaleAlert()}

      {/* Filter and Search Bar */}
      <Row className="g-2 mb-3">
        {/* Search Query Input */}
        <Col xs={12} md={7}>
          <Form.Group controlId={`${idPrefix}-search`}>
            <InputGroup>
              <InputGroup.Text
                id={`${idPrefix}-search-icon`}
                className="bg-light"
              >
                <FiSearch aria-hidden="true" />
              </InputGroup.Text>
              <Form.Control
                type="text"
                placeholder="Tìm mã thiết bị, mô tả..."
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                disabled={disabled}
                isInvalid={isInvalid}
                aria-label="Tìm kiếm thiết bị"
                aria-describedby={`${idPrefix}-search-icon`}
              />
              {searchInput && (
                <Button
                  variant="outline-secondary"
                  onClick={() => setSearchInput('')}
                  aria-label="Xóa từ khóa tìm kiếm"
                >
                  <FiX aria-hidden="true" />
                </Button>
              )}
            </InputGroup>
          </Form.Group>
        </Col>

        {/* Equipment Type Filter Dropdown */}
        <Col xs={12} md={5}>
          <Form.Group controlId={`${idPrefix}-eqtype`}>
            <Form.Select
              value={equipmentTypeId}
              onChange={handleEquipmentTypeChange}
              disabled={disabled || equipmentTypesQuery.isLoading}
              isInvalid={isInvalid}
              aria-label="Lọc theo loại thiết bị"
            >
              <option value="">-- Tất cả loại thiết bị --</option>
              {equipmentTypesQuery.data?.map((type) => (
                <option key={type.id} value={type.id}>
                  {type.name} ({type.code})
                </option>
              ))}
            </Form.Select>
          </Form.Group>
          {equipmentTypesQuery.isError && (
            <div className="text-danger small mt-1 d-flex justify-content-between align-items-center">
              <span>Lỗi tải loại thiết bị</span>
              <Button
                variant="link"
                size="sm"
                className="p-0 text-danger"
                onClick={() => equipmentTypesQuery.refetch()}
              >
                <FiRefreshCw className="me-1" /> Thử lại
              </Button>
            </div>
          )}
        </Col>
      </Row>

      {/* List / Results Container */}
      <div className="border rounded bg-white overflow-hidden shadow-sm">
        {eligibleAssetsQuery.isLoading ? (
          <div className="py-4">
            <LoadingSpinner label="Đang tải danh sách thiết bị hợp lệ..." />
          </div>
        ) : eligibleAssetsQuery.isError ? (
          <div className="p-3">
            <Alert
              variant="warning"
              className="mb-0 d-flex justify-content-between align-items-center"
            >
              <div>
                <strong>Không tải được danh sách thiết bị.</strong>
                <p className="mb-0 small">
                  {eligibleAssetsQuery.error?.response?.status === 400
                    ? 'Tham số tìm kiếm không hợp lệ.'
                    : 'Đã xảy ra lỗi khi kết nối tới máy chủ.'}
                </p>
              </div>
              <Button
                variant="outline-warning"
                size="sm"
                onClick={() => eligibleAssetsQuery.refetch()}
                className="d-inline-flex align-items-center gap-1"
              >
                <FiRefreshCw aria-hidden="true" /> Thử lại
              </Button>
            </Alert>
          </div>
        ) : items.length === 0 ? (
          <div className="py-3">
            <EmptyState
              message={
                debouncedQuery || equipmentTypeId
                  ? 'Không tìm thấy thiết bị phù hợp với bộ lọc.'
                  : 'Không có thiết bị hợp lệ tại địa điểm này.'
              }
            />
          </div>
        ) : (
          <>
            <ListGroup variant="flush">
              {items.map((asset) => {
                const isCurrentSelected = value?.id === asset.id
                return (
                  <ListGroup.Item
                    key={asset.id}
                    className={`d-flex align-items-center justify-content-between flex-wrap gap-2 py-3 px-3 ${
                      isCurrentSelected ? 'bg-light' : ''
                    }`}
                  >
                    <div className="d-flex align-items-start gap-2">
                      <FiBox
                        className="text-primary mt-1 flex-shrink-0"
                        aria-hidden="true"
                      />
                      <div>
                        <div className="d-flex align-items-center gap-2 flex-wrap">
                          <strong className="text-dark">
                            {asset.assetCode}
                          </strong>
                          {asset.name && (
                            <span className="text-secondary">
                              — {asset.name}
                            </span>
                          )}
                          {asset.equipmentType?.name && (
                            <Badge bg="secondary" className="small">
                              {asset.equipmentType.name}
                            </Badge>
                          )}
                          <AssetStatusBadge status={asset.status} />
                        </div>
                        {asset.description && (
                          <div className="text-secondary small mt-1">
                            {asset.description}
                          </div>
                        )}
                      </div>
                    </div>

                    <div>
                      <Button
                        size="sm"
                        variant={
                          isCurrentSelected ? 'success' : 'outline-primary'
                        }
                        disabled={disabled}
                        onClick={() => handleSelectAsset(asset)}
                        className="d-inline-flex align-items-center gap-1"
                        aria-label={`Chọn thiết bị ${asset.assetCode}`}
                      >
                        {isCurrentSelected ? (
                          <>
                            <FiCheck aria-hidden="true" /> Đang chọn
                          </>
                        ) : (
                          'Chọn'
                        )}
                      </Button>
                    </div>
                  </ListGroup.Item>
                )
              })}
            </ListGroup>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="d-flex align-items-center justify-content-between border-top p-2 bg-light flex-wrap gap-2">
                <small className="text-secondary">
                  Hiển thị {page * pageSize + 1} -{' '}
                  {Math.min((page + 1) * pageSize, totalElements)} trong tổng số{' '}
                  {totalElements} thiết bị
                </small>

                <Stack direction="horizontal" gap={1}>
                  <Button
                    size="sm"
                    variant="outline-secondary"
                    disabled={disabled || page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    aria-label="Trang trước"
                  >
                    Trước
                  </Button>
                  <span className="small text-secondary px-2">
                    {page + 1} / {totalPages}
                  </span>
                  <Button
                    size="sm"
                    variant="outline-secondary"
                    disabled={disabled || page + 1 >= totalPages}
                    onClick={() => setPage((p) => p + 1)}
                    aria-label="Trang sau"
                  >
                    Sau
                  </Button>
                </Stack>
              </div>
            )}
          </>
        )}
      </div>

      {/* Field Validation Error Feedback */}
      {error && (
        <Form.Control.Feedback type="invalid" className="d-block mt-2">
          {error}
        </Form.Control.Feedback>
      )}
    </div>
  )
}
