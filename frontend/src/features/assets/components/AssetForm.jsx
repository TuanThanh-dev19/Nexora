import { useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  ListGroup,
  Row,
  Spinner,
  Stack,
} from 'react-bootstrap'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEquipmentTypes } from '../../equipmentTypes/hooks/useEquipmentTypes'
import { useLocation, useLocations } from '../../locations/hooks/useLocations'
import { assetApi } from '../api/assetApi'
import { assetKeys } from '../hooks/useAssets'
import {
  extractFieldErrors,
  formatAssetError,
  isConflictError,
} from '../api/assetError'

export function AssetForm({
  assetToEdit = null,
  onSuccess,
  onCancel,
  submitLabel,
}) {
  const queryClient = useQueryClient()
  const isEdit = Boolean(assetToEdit?.id)

  // Current working asset (can be refreshed on 409)
  const [currentAsset, setCurrentAsset] = useState(assetToEdit)

  // Form states
  const [assetCode, setAssetCode] = useState(
    () => currentAsset?.assetCode || '',
  )
  const [equipmentTypeId, setEquipmentTypeId] = useState(() =>
    currentAsset?.equipmentType?.id
      ? String(currentAsset.equipmentType.id)
      : '',
  )
  const [locationId, setLocationId] = useState(() =>
    currentAsset?.location?.id ? String(currentAsset.location.id) : '',
  )
  const [serialNumber, setSerialNumber] = useState(
    () => currentAsset?.serialNumber || '',
  )
  const [description, setDescription] = useState(
    () => currentAsset?.description || '',
  )
  const [purchasedAt, setPurchasedAt] = useState(
    () => currentAsset?.purchasedAt || '',
  )
  const [warrantyUntil, setWarrantyUntil] = useState(
    () => currentAsset?.warrantyUntil || '',
  )

  // Location selector navigation trail
  const [trail, setTrail] = useState([])
  const [showLocationPicker, setShowLocationPicker] = useState(false)

  // Fetch active equipment types
  const equipmentTypesQuery = useEquipmentTypes(true)

  // Selected location details
  const selectedLocationQuery = useLocation(
    locationId ? Number(locationId) : null,
  )
  const currentLocation = selectedLocationQuery.data ?? currentAsset?.location

  // Child locations for browser
  const parentLocationId = trail.at(-1)?.id ?? null
  const childLocationsQuery = useLocations(parentLocationId, {
    active: true,
    enabled: showLocationPicker,
  })

  // Errors & notifications
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState(null)
  const [conflictOccurred, setConflictOccurred] = useState(false)
  const [isReloading, setIsReloading] = useState(false)

  // Reload latest data on 409 conflict
  const handleReloadLatest = async () => {
    if (!currentAsset?.id) return
    setIsReloading(true)
    setServerError(null)
    setConflictOccurred(false)
    try {
      const fresh = await assetApi.getById(currentAsset.id)
      setCurrentAsset(fresh)
      setEquipmentTypeId(
        fresh.equipmentType?.id ? String(fresh.equipmentType.id) : '',
      )
      setLocationId(fresh.location?.id ? String(fresh.location.id) : '')
      setSerialNumber(fresh.serialNumber || '')
      setDescription(fresh.description || '')
      setPurchasedAt(fresh.purchasedAt || '')
      setWarrantyUntil(fresh.warrantyUntil || '')
      setErrors({})
    } catch (err) {
      setServerError(
        formatAssetError(
          err,
          'Không thể tải dữ liệu mới nhất. Vui lòng đóng cửa sổ và thử lại.',
        ),
      )
    } finally {
      setIsReloading(false)
    }
  }

  // Mutation
  const mutation = useMutation({
    mutationFn: async () => {
      if (isEdit) {
        return assetApi.update(currentAsset.id, {
          version: currentAsset.version,
          equipmentTypeId: Number(equipmentTypeId),
          locationId: Number(locationId),
          serialNumber: serialNumber.trim() || null,
          description: description.trim() || null,
          purchasedAt: purchasedAt || null,
          warrantyUntil: warrantyUntil || null,
        })
      } else {
        return assetApi.create({
          assetCode: assetCode.trim(),
          equipmentTypeId: Number(equipmentTypeId),
          locationId: Number(locationId),
          serialNumber: serialNumber.trim() || null,
          description: description.trim() || null,
          purchasedAt: purchasedAt || null,
          warrantyUntil: warrantyUntil || null,
        })
      }
    },
    onSuccess: async (savedAsset) => {
      await queryClient.invalidateQueries({ queryKey: assetKeys.all })
      if (onSuccess) {
        onSuccess(savedAsset)
      }
    },
    onError: (err) => {
      if (isConflictError(err)) {
        setConflictOccurred(true)
        setServerError(
          'Xung đột phiên bản: Dữ liệu tài sản đã được cập nhật bởi một người dùng hoặc tiến trình khác. Vui lòng bấm "Tải lại dữ liệu mới nhất" để cập nhật và kiểm tra trước khi lưu.',
        )
      } else {
        const fieldErrors = extractFieldErrors(err)
        if (Object.keys(fieldErrors).length > 0) {
          setErrors(fieldErrors)
        }
        setServerError(formatAssetError(err))
      }
    },
  })

  // Validation
  const validate = () => {
    const newErrors = {}

    if (!isEdit) {
      if (!assetCode.trim()) {
        newErrors.assetCode = 'Mã tài sản không được để trống.'
      } else if (assetCode.trim().length > 80) {
        newErrors.assetCode = 'Mã tài sản không được vượt quá 80 ký tự.'
      }
    }

    if (!equipmentTypeId) {
      newErrors.equipmentTypeId = 'Vui lòng chọn loại thiết bị.'
    }

    if (!locationId) {
      newErrors.locationId = 'Vui lòng chọn địa điểm đặt tài sản.'
    }

    if (serialNumber && serialNumber.trim().length > 120) {
      newErrors.serialNumber = 'Số sê-ri không được vượt quá 120 ký tự.'
    }

    if (description && description.trim().length > 500) {
      newErrors.description = 'Mô tả không được vượt quá 500 ký tự.'
    }

    if (purchasedAt && warrantyUntil && warrantyUntil < purchasedAt) {
      newErrors.warrantyUntil =
        'Ngày hết hạn bảo hành không thể trước ngày mua.'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    setServerError(null)
    if (!validate()) return
    mutation.mutate()
  }

  return (
    <Form noValidate onSubmit={handleSubmit}>
      {serverError && (
        <Alert
          variant={conflictOccurred ? 'warning' : 'danger'}
          dismissible={!conflictOccurred}
          onClose={() => setServerError(null)}
          className="mb-4"
        >
          <div>{serverError}</div>
          {conflictOccurred && (
            <div className="mt-2">
              <Button
                variant="primary"
                size="sm"
                disabled={isReloading}
                onClick={handleReloadLatest}
              >
                {isReloading ? (
                  <>
                    <Spinner animation="border" size="sm" className="me-1" />
                    Đang tải dữ liệu mới...
                  </>
                ) : (
                  'Tải lại dữ liệu mới nhất'
                )}
              </Button>
            </div>
          )}
        </Alert>
      )}

      <fieldset disabled={mutation.isPending || isReloading}>
        <Row className="g-3">
          {/* Asset Code */}
          <Form.Group as={Col} md={6} controlId="asset-code">
            <Form.Label>
              Mã tài sản {!isEdit && <span className="text-danger">*</span>}
            </Form.Label>
            <Form.Control
              type="text"
              required={!isEdit}
              readOnly={isEdit}
              disabled={isEdit}
              maxLength={80}
              placeholder="Ví dụ: LAPTOP-DELL-001"
              value={assetCode}
              onChange={(e) => setAssetCode(e.target.value)}
              isInvalid={Boolean(errors.assetCode)}
            />
            <Form.Control.Feedback type="invalid">
              {errors.assetCode}
            </Form.Control.Feedback>
            <Form.Text className="text-muted">
              {isEdit
                ? 'Mã tài sản là cố định và không thể chỉnh sửa.'
                : 'Mã định danh duy nhất của tài sản (tối đa 80 ký tự).'}
            </Form.Text>
          </Form.Group>

          {/* Serial Number */}
          <Form.Group as={Col} md={6} controlId="asset-serial">
            <Form.Label>Số sê-ri</Form.Label>
            <Form.Control
              type="text"
              maxLength={120}
              placeholder="Ví dụ: SN-9876543210"
              value={serialNumber}
              onChange={(e) => setSerialNumber(e.target.value)}
              isInvalid={Boolean(errors.serialNumber)}
            />
            <Form.Control.Feedback type="invalid">
              {errors.serialNumber}
            </Form.Control.Feedback>
          </Form.Group>

          {/* Equipment Type */}
          <Form.Group as={Col} md={6} controlId="asset-equipment-type">
            <Form.Label>
              Loại thiết bị <span className="text-danger">*</span>
            </Form.Label>
            <Form.Select
              value={equipmentTypeId}
              onChange={(e) => setEquipmentTypeId(e.target.value)}
              isInvalid={Boolean(errors.equipmentTypeId)}
              disabled={equipmentTypesQuery.isPending}
            >
              <option value="">-- Chọn loại thiết bị --</option>
              {currentAsset?.equipmentType &&
                !equipmentTypesQuery.data?.some(
                  (t) => t.id === currentAsset.equipmentType.id,
                ) && (
                  <option value={currentAsset.equipmentType.id}>
                    {currentAsset.equipmentType.name} (
                    {currentAsset.equipmentType.code}) [Không hoạt động]
                  </option>
                )}
              {equipmentTypesQuery.data?.map((type) => (
                <option key={type.id} value={type.id}>
                  {type.name} ({type.code})
                </option>
              ))}
            </Form.Select>
            <Form.Control.Feedback type="invalid">
              {errors.equipmentTypeId}
            </Form.Control.Feedback>
          </Form.Group>

          {/* Location Selector */}
          <Col md={6}>
            <Form.Label className="d-block">
              Địa điểm đặt tài sản <span className="text-danger">*</span>
            </Form.Label>
            <div className="border rounded p-2 bg-light">
              <Stack
                direction="horizontal"
                gap={2}
                className="justify-content-between flex-wrap"
              >
                <span className="small">
                  {currentLocation
                    ? `${currentLocation.name} (${currentLocation.code})`
                    : 'Chưa chọn địa điểm'}
                </span>
                <Button
                  size="sm"
                  variant="outline-primary"
                  onClick={() => setShowLocationPicker(!showLocationPicker)}
                >
                  {showLocationPicker
                    ? 'Đóng chọn địa điểm'
                    : 'Thay đổi địa điểm'}
                </Button>
              </Stack>
            </div>
            {errors.locationId && (
              <div className="text-danger small mt-1">{errors.locationId}</div>
            )}
          </Col>

          {/* Hierarchical Location Browser Popup / Collapsible */}
          {showLocationPicker && (
            <Col xs={12}>
              <Card className="bg-light border p-3">
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <span className="fw-semibold small">
                    {trail.length > 0
                      ? `Đang xem: ${trail.map((t) => t.name).join(' / ')}`
                      : 'Các cơ sở / Địa điểm gốc'}
                  </span>
                  {trail.length > 0 && (
                    <Button
                      variant="link"
                      size="sm"
                      className="p-0 text-decoration-none"
                      onClick={() => setTrail(trail.slice(0, -1))}
                    >
                      ← Quay lên
                    </Button>
                  )}
                </div>

                {childLocationsQuery.isPending ? (
                  <div className="text-secondary small py-2">
                    Đang tải danh sách địa điểm...
                  </div>
                ) : childLocationsQuery.isError ? (
                  <Alert variant="warning" className="small py-1 mb-0">
                    Không thể tải địa điểm.
                  </Alert>
                ) : childLocationsQuery.data?.length === 0 ? (
                  <div className="text-secondary small py-2">
                    Không có địa điểm con hoạt động.
                  </div>
                ) : (
                  <ListGroup
                    className="small"
                    style={{ maxHeight: '180px', overflowY: 'auto' }}
                  >
                    {childLocationsQuery.data?.map((loc) => (
                      <ListGroup.Item
                        key={loc.id}
                        className="d-flex justify-content-between align-items-center py-1"
                      >
                        <span>
                          {loc.name}{' '}
                          <code className="text-muted">({loc.code})</code>
                        </span>
                        <span>
                          <Button
                            size="sm"
                            variant={
                              String(loc.id) === String(locationId)
                                ? 'success'
                                : 'outline-primary'
                            }
                            className="py-0 px-2 me-1"
                            aria-label={`Chọn địa điểm ${loc.name}`}
                            onClick={() => {
                              setLocationId(String(loc.id))
                              setShowLocationPicker(false)
                            }}
                          >
                            {String(loc.id) === String(locationId)
                              ? 'Đã chọn'
                              : 'Chọn'}
                          </Button>
                          {!['ROOM', 'AREA'].includes(loc.type) && (
                            <Button
                              size="sm"
                              variant="link"
                              className="py-0 px-1 text-decoration-none"
                              onClick={() => setTrail([...trail, loc])}
                            >
                              Xem con →
                            </Button>
                          )}
                        </span>
                      </ListGroup.Item>
                    ))}
                  </ListGroup>
                )}
              </Card>
            </Col>
          )}

          {/* Purchase Date */}
          <Form.Group as={Col} md={6} controlId="asset-purchase-date">
            <Form.Label>Ngày mua</Form.Label>
            <Form.Control
              type="date"
              value={purchasedAt}
              onChange={(e) => setPurchasedAt(e.target.value)}
              isInvalid={Boolean(errors.purchasedAt)}
            />
            <Form.Control.Feedback type="invalid">
              {errors.purchasedAt}
            </Form.Control.Feedback>
          </Form.Group>

          {/* Warranty Expiration Date */}
          <Form.Group as={Col} md={6} controlId="asset-warranty-date">
            <Form.Label>Hạn bảo hành</Form.Label>
            <Form.Control
              type="date"
              value={warrantyUntil}
              onChange={(e) => setWarrantyUntil(e.target.value)}
              isInvalid={Boolean(errors.warrantyUntil)}
            />
            <Form.Control.Feedback type="invalid">
              {errors.warrantyUntil}
            </Form.Control.Feedback>
          </Form.Group>

          {/* Description */}
          <Form.Group as={Col} xs={12} controlId="asset-description">
            <Form.Label>Mô tả tài sản</Form.Label>
            <Form.Control
              as="textarea"
              rows={3}
              maxLength={500}
              placeholder="Ghi chú chi tiết về tình trạng, cấu hình hoặc đặc điểm của tài sản..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              isInvalid={Boolean(errors.description)}
            />
            <Form.Control.Feedback type="invalid">
              {errors.description}
            </Form.Control.Feedback>
          </Form.Group>
        </Row>
      </fieldset>

      <div className="d-flex justify-content-end gap-2 mt-4">
        {onCancel && (
          <Button
            variant="secondary"
            disabled={mutation.isPending || isReloading}
            onClick={onCancel}
          >
            Hủy
          </Button>
        )}
        <Button
          variant="primary"
          type="submit"
          disabled={mutation.isPending || isReloading || conflictOccurred}
        >
          {mutation.isPending ? (
            <>
              <Spinner animation="border" size="sm" className="me-2" />
              Đang lưu…
            </>
          ) : (
            (submitLabel ?? (isEdit ? 'Cập nhật' : 'Tạo tài sản'))
          )}
        </Button>
      </div>
    </Form>
  )
}
