import { useState } from 'react'
import {
  Alert,
  Button,
  Col,
  Form,
  Modal,
  Row,
  Spinner,
} from 'react-bootstrap'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  LOCATION_TYPE_CODES,
  LOCATION_TYPES,
  LOCATION_TYPE_LABELS,
  getExpectedParentType,
} from '../constants/locationTypes'
import { locationApi } from '../api/locationApi'
import { locationKeys, useLocations } from '../hooks/useLocations'
import { formatLocationError } from '../api/locationError'

function LocationModalForm({
  locationToEdit,
  initialParent,
  onHide,
  onSuccessNotice,
}) {
  const queryClient = useQueryClient()
  const isEdit = Boolean(locationToEdit?.id)

  const [code, setCode] = useState(() => locationToEdit?.code || '')
  const [name, setName] = useState(() => locationToEdit?.name || '')
  const [type, setType] = useState(() => {
    if (locationToEdit?.type) return locationToEdit.type
    if (initialParent) {
      if (initialParent.type === LOCATION_TYPE_CODES.CAMPUS) return LOCATION_TYPE_CODES.BUILDING
      if (initialParent.type === LOCATION_TYPE_CODES.BUILDING) return LOCATION_TYPE_CODES.FLOOR
      if (initialParent.type === LOCATION_TYPE_CODES.FLOOR) return LOCATION_TYPE_CODES.ROOM
    }
    return LOCATION_TYPE_CODES.CAMPUS
  })
  const [parentId, setParentId] = useState(() => {
    if (locationToEdit) return locationToEdit.parentId ?? null
    if (initialParent && initialParent.type !== LOCATION_TYPE_CODES.ROOM && initialParent.type !== LOCATION_TYPE_CODES.AREA) {
      return initialParent.id
    }
    return null
  })
  const [description, setDescription] = useState(() => locationToEdit?.description || '')
  const [active, setActive] = useState(() => (locationToEdit ? Boolean(locationToEdit.active) : true))

  // Cascade selector helper states
  const [selectedCampusId, setSelectedCampusId] = useState(() => {
    if (initialParent?.type === LOCATION_TYPE_CODES.CAMPUS) return String(initialParent.id)
    return ''
  })
  const [selectedBuildingId, setSelectedBuildingId] = useState(() => {
    if (initialParent?.type === LOCATION_TYPE_CODES.BUILDING) return String(initialParent.id)
    return ''
  })

  // Validation and server errors
  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState(null)

  // Determine what parent type is expected
  const expectedParentType = getExpectedParentType(type)

  // Queries for parent cascade selector
  const campusesQuery = useLocations(null, {
    active: false,
    enabled: expectedParentType != null,
  })

  const buildingsQuery = useLocations(selectedCampusId ? Number(selectedCampusId) : null, {
    active: false,
    enabled: Boolean(selectedCampusId) && [LOCATION_TYPE_CODES.BUILDING, LOCATION_TYPE_CODES.FLOOR].includes(expectedParentType),
  })

  const floorsQuery = useLocations(selectedBuildingId ? Number(selectedBuildingId) : null, {
    active: false,
    enabled: Boolean(selectedBuildingId) && expectedParentType === LOCATION_TYPE_CODES.FLOOR,
  })

  // When type changes in create mode, reset parent if incompatible
  const handleTypeChange = (newType) => {
    setType(newType)
    const expected = getExpectedParentType(newType)
    if (!expected) {
      setParentId(null)
      setSelectedCampusId('')
      setSelectedBuildingId('')
    }
  }

  // Mutation for create or update
  const mutation = useMutation({
    mutationFn: async () => {
      if (isEdit) {
        return locationApi.update(locationToEdit.id, {
          name,
          parentId,
          description,
          active,
        })
      } else {
        return locationApi.create({
          code,
          name,
          type,
          parentId,
          description,
        })
      }
    },
    onSuccess: async (savedLocation) => {
      await queryClient.invalidateQueries({ queryKey: locationKeys.all })
      onHide()
      onSuccessNotice(
        isEdit
          ? `Đã cập nhật địa điểm "${savedLocation.name}" (${savedLocation.code}) thành công.`
          : `Đã tạo địa điểm "${savedLocation.name}" (${savedLocation.code}) thành công.`,
      )
    },
    onError: (err) => {
      const fieldErrors = err.response?.data?.fieldErrors
      if (fieldErrors && typeof fieldErrors === 'object') {
        setErrors(fieldErrors)
      }
      setServerError(formatLocationError(err))
    },
  })

  // Client validation
  const validate = () => {
    const newErrors = {}

    if (!isEdit) {
      if (!code.trim()) {
        newErrors.code = 'Mã địa điểm không được để trống.'
      } else if (!/^[A-Za-z0-9_-]{1,50}$/.test(code.trim())) {
        newErrors.code = 'Mã gồm 1-50 ký tự (chữ, số, gạch ngang, gạch dưới).'
      }
    }

    if (!name.trim()) {
      newErrors.name = 'Tên địa điểm không được để trống.'
    } else if (name.trim().length > 150) {
      newErrors.name = 'Tên địa điểm không được vượt quá 150 ký tự.'
    }

    if (description && description.length > 500) {
      newErrors.description = 'Mô tả không được vượt quá 500 ký tự.'
    }

    if (expectedParentType && parentId == null) {
      newErrors.parentId = `Loại ${LOCATION_TYPE_LABELS[type]} yêu cầu phải chọn địa điểm cha (${LOCATION_TYPE_LABELS[expectedParentType]}).`
    }

    if (isEdit && parentId === locationToEdit.id) {
      newErrors.parentId = 'Địa điểm không thể chọn chính nó làm địa điểm cha.'
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
      <Modal.Body>
        {serverError && (
          <Alert variant="danger" dismissible onClose={() => setServerError(null)}>
            {serverError}
          </Alert>
        )}

        <fieldset disabled={mutation.isPending}>
          <Row className="g-3">
            {/* Code Field */}
            <Form.Group as={Col} md={6} controlId="location-code">
              <Form.Label>
                Mã địa điểm {!isEdit && <span className="text-danger">*</span>}
              </Form.Label>
              <Form.Control
                type="text"
                required={!isEdit}
                readOnly={isEdit}
                disabled={isEdit}
                maxLength={50}
                placeholder="Ví dụ: CS1, TOA-A, TANG-3, P302"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                isInvalid={Boolean(errors.code)}
              />
              <Form.Control.Feedback type="invalid">
                {errors.code}
              </Form.Control.Feedback>
              <Form.Text className="text-muted">
                {isEdit
                  ? 'Mã địa điểm là cố định và không thể thay đổi sau khi tạo.'
                  : 'Mã định danh duy nhất trong hệ thống (tối đa 50 ký tự).'}
              </Form.Text>
            </Form.Group>

            {/* Type Field */}
            <Form.Group as={Col} md={6} controlId="location-type">
              <Form.Label>
                Loại địa điểm <span className="text-danger">*</span>
              </Form.Label>
              {isEdit ? (
                <Form.Control
                  type="text"
                  readOnly
                  disabled
                  value={`${LOCATION_TYPE_LABELS[type] ?? type} (${type})`}
                />
              ) : (
                <Form.Select
                  value={type}
                  onChange={(e) => handleTypeChange(e.target.value)}
                  isInvalid={Boolean(errors.type)}
                >
                  {LOCATION_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {LOCATION_TYPE_LABELS[t]} ({t})
                    </option>
                  ))}
                </Form.Select>
              )}
              <Form.Control.Feedback type="invalid">
                {errors.type}
              </Form.Control.Feedback>
              <Form.Text className="text-muted">
                {expectedParentType
                  ? `Yêu cầu địa điểm cha cấp: ${LOCATION_TYPE_LABELS[expectedParentType]}`
                  : 'Cơ sở là địa điểm gốc cao nhất (không có địa điểm cha).'}
              </Form.Text>
            </Form.Group>

            {/* Name Field */}
            <Form.Group as={Col} xs={12} controlId="location-name">
              <Form.Label>
                Tên địa điểm <span className="text-danger">*</span>
              </Form.Label>
              <Form.Control
                type="text"
                required
                maxLength={150}
                placeholder="Ví dụ: Cơ sở 1, Tòa nhà Alpha, Tầng 3, Phòng Thực hành 302"
                value={name}
                onChange={(e) => setName(e.target.value)}
                isInvalid={Boolean(errors.name)}
              />
              <Form.Control.Feedback type="invalid">
                {errors.name}
              </Form.Control.Feedback>
            </Form.Group>

            {/* Parent Selector */}
            {expectedParentType && (
              <Col xs={12}>
                <div className="p-3 bg-light rounded border">
                  <Form.Label className="fw-semibold">
                    Địa điểm cha ({LOCATION_TYPE_LABELS[expectedParentType]}){' '}
                    <span className="text-danger">*</span>
                  </Form.Label>

                  {expectedParentType === LOCATION_TYPE_CODES.CAMPUS && (
                    <Form.Select
                      value={parentId ?? ''}
                      onChange={(e) => setParentId(e.target.value ? Number(e.target.value) : null)}
                      isInvalid={Boolean(errors.parentId)}
                    >
                      <option value="">-- Chọn Cơ sở (CAMPUS) --</option>
                      {campusesQuery.data?.map((campus) => (
                        <option key={campus.id} value={campus.id}>
                          {campus.name} ({campus.code})
                        </option>
                      ))}
                    </Form.Select>
                  )}

                  {expectedParentType === LOCATION_TYPE_CODES.BUILDING && (
                    <Row className="g-2">
                      <Col sm={6}>
                        <Form.Label className="small text-secondary">
                          1. Lọc theo Cơ sở:
                        </Form.Label>
                        <Form.Select
                          value={selectedCampusId}
                          onChange={(e) => {
                            setSelectedCampusId(e.target.value)
                            setParentId(null)
                          }}
                        >
                          <option value="">-- Tất cả cơ sở --</option>
                          {campusesQuery.data?.map((campus) => (
                            <option key={campus.id} value={campus.id}>
                              {campus.name}
                            </option>
                          ))}
                        </Form.Select>
                      </Col>
                      <Col sm={6}>
                        <Form.Label className="small text-secondary">
                          2. Chọn Tòa nhà cha:
                        </Form.Label>
                        <Form.Select
                          value={parentId ?? ''}
                          onChange={(e) => setParentId(e.target.value ? Number(e.target.value) : null)}
                          isInvalid={Boolean(errors.parentId)}
                        >
                          <option value="">-- Chọn Tòa nhà --</option>
                          {buildingsQuery.data?.map((b) => (
                            <option
                              key={b.id}
                              value={b.id}
                              disabled={isEdit && b.id === locationToEdit?.id}
                            >
                              {b.name} ({b.code})
                            </option>
                          ))}
                        </Form.Select>
                      </Col>
                    </Row>
                  )}

                  {expectedParentType === LOCATION_TYPE_CODES.FLOOR && (
                    <Row className="g-2">
                      <Col sm={4}>
                        <Form.Label className="small text-secondary">
                          1. Cơ sở:
                        </Form.Label>
                        <Form.Select
                          value={selectedCampusId}
                          onChange={(e) => {
                            setSelectedCampusId(e.target.value)
                            setSelectedBuildingId('')
                            setParentId(null)
                          }}
                        >
                          <option value="">-- Chọn cơ sở --</option>
                          {campusesQuery.data?.map((campus) => (
                            <option key={campus.id} value={campus.id}>
                              {campus.name}
                            </option>
                          ))}
                        </Form.Select>
                      </Col>
                      <Col sm={4}>
                        <Form.Label className="small text-secondary">
                          2. Tòa nhà:
                        </Form.Label>
                        <Form.Select
                          value={selectedBuildingId}
                          disabled={!selectedCampusId}
                          onChange={(e) => {
                            setSelectedBuildingId(e.target.value)
                            setParentId(null)
                          }}
                        >
                          <option value="">-- Chọn tòa nhà --</option>
                          {buildingsQuery.data?.map((b) => (
                            <option key={b.id} value={b.id}>
                              {b.name} ({b.code})
                            </option>
                          ))}
                        </Form.Select>
                      </Col>
                      <Col sm={4}>
                        <Form.Label className="small text-secondary">
                          3. Chọn Tầng cha:
                        </Form.Label>
                        <Form.Select
                          value={parentId ?? ''}
                          disabled={!selectedBuildingId}
                          onChange={(e) => setParentId(e.target.value ? Number(e.target.value) : null)}
                          isInvalid={Boolean(errors.parentId)}
                        >
                          <option value="">-- Chọn Tầng --</option>
                          {floorsQuery.data?.map((f) => (
                            <option
                              key={f.id}
                              value={f.id}
                              disabled={isEdit && f.id === locationToEdit?.id}
                            >
                              {f.name} ({f.code})
                            </option>
                          ))}
                        </Form.Select>
                      </Col>
                    </Row>
                  )}

                  {errors.parentId && (
                    <div className="text-danger small mt-2">{errors.parentId}</div>
                  )}
                </div>
              </Col>
            )}

            {/* Description Field */}
            <Form.Group as={Col} xs={12} controlId="location-description">
              <Form.Label>Mô tả (tùy chọn)</Form.Label>
              <Form.Control
                as="textarea"
                rows={3}
                maxLength={500}
                placeholder="Ghi chú thêm về chức năng hoặc vị trí cụ thể của địa điểm..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                isInvalid={Boolean(errors.description)}
              />
              <Form.Control.Feedback type="invalid">
                {errors.description}
              </Form.Control.Feedback>
            </Form.Group>

            {/* Active Toggle (Edit mode) */}
            {isEdit && (
              <Col xs={12}>
                <Form.Check
                  type="switch"
                  id="location-active-switch"
                  label="Địa điểm đang hoạt động"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                />
                <Form.Text className="text-muted">
                  Khi tắt, địa điểm sẽ chuyển sang trạng thái ngừng hoạt động và không thể chọn khi tạo phiếu mới.
                </Form.Text>
              </Col>
            )}
          </Row>
        </fieldset>
      </Modal.Body>

      <Modal.Footer>
        <Button
          variant="secondary"
          disabled={mutation.isPending}
          onClick={onHide}
        >
          Hủy
        </Button>
        <Button
          variant="primary"
          type="submit"
          disabled={mutation.isPending}
        >
          {mutation.isPending ? (
            <>
              <Spinner animation="border" size="sm" className="me-2" />
              Đang lưu…
            </>
          ) : isEdit ? (
            'Cập nhật'
          ) : (
            'Tạo địa điểm'
          )}
        </Button>
      </Modal.Footer>
    </Form>
  )
}

export function LocationModal({
  show,
  onHide,
  locationToEdit,
  initialParent,
  onSuccessNotice,
}) {
  const isEdit = Boolean(locationToEdit?.id)

  return (
    <Modal show={show} onHide={onHide} backdrop="static" size="lg">
      <Modal.Header closeButton>
        <Modal.Title as="h2" className="h5 mb-0">
          {isEdit ? `Chỉnh sửa địa điểm: ${locationToEdit.name}` : 'Thêm địa điểm mới'}
        </Modal.Title>
      </Modal.Header>

      {show && (
        <LocationModalForm
          key={`${locationToEdit?.id ?? 'create'}-${initialParent?.id ?? ''}`}
          locationToEdit={locationToEdit}
          initialParent={initialParent}
          onHide={onHide}
          onSuccessNotice={onSuccessNotice}
        />
      )}
    </Modal>
  )
}
