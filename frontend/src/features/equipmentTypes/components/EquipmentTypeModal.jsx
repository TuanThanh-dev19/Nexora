import { useState } from 'react'
import { Alert, Button, Col, Form, Modal, Row, Spinner } from 'react-bootstrap'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { equipmentTypeApi } from '../api/equipmentTypeApi'
import { equipmentTypeKeys } from '../hooks/useEquipmentTypes'
import { formatEquipmentTypeError } from '../api/equipmentTypeError'

function EquipmentTypeModalForm({
  equipmentTypeToEdit,
  onHide,
  onSuccessNotice,
}) {
  const queryClient = useQueryClient()
  const isEdit = Boolean(equipmentTypeToEdit?.id)

  const [code, setCode] = useState(() => equipmentTypeToEdit?.code || '')
  const [name, setName] = useState(() => equipmentTypeToEdit?.name || '')
  const [description, setDescription] = useState(() => equipmentTypeToEdit?.description || '')
  const [active, setActive] = useState(() => (equipmentTypeToEdit ? Boolean(equipmentTypeToEdit.active) : true))

  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState(null)

  const mutation = useMutation({
    mutationFn: async () => {
      if (isEdit) {
        return equipmentTypeApi.update(equipmentTypeToEdit.id, {
          name,
          description,
          active,
        })
      } else {
        return equipmentTypeApi.create({
          code,
          name,
          description,
        })
      }
    },
    onSuccess: async (savedType) => {
      await queryClient.invalidateQueries({ queryKey: equipmentTypeKeys.all })
      onHide()
      onSuccessNotice(
        isEdit
          ? `Đã cập nhật loại thiết bị "${savedType.name}" (${savedType.code}) thành công.`
          : `Đã tạo loại thiết bị "${savedType.name}" (${savedType.code}) thành công.`,
      )
    },
    onError: (err) => {
      const fieldErrors = err.response?.data?.fieldErrors
      if (fieldErrors && typeof fieldErrors === 'object') {
        setErrors(fieldErrors)
      }
      setServerError(formatEquipmentTypeError(err))
    },
  })

  const validate = () => {
    const newErrors = {}

    if (!isEdit) {
      if (!code.trim()) {
        newErrors.code = 'Mã loại thiết bị không được để trống.'
      } else if (!/^[A-Za-z0-9_-]{1,50}$/.test(code.trim())) {
        newErrors.code = 'Mã gồm 1-50 ký tự (chữ, số, gạch ngang, gạch dưới).'
      }
    }

    if (!name.trim()) {
      newErrors.name = 'Tên loại thiết bị không được để trống.'
    } else if (name.trim().length > 150) {
      newErrors.name = 'Tên không được vượt quá 150 ký tự.'
    }

    if (description && description.length > 500) {
      newErrors.description = 'Mô tả không được vượt quá 500 ký tự.'
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
            {/* Code */}
            <Form.Group as={Col} xs={12} controlId="equipment-type-code">
              <Form.Label>
                Mã loại thiết bị {!isEdit && <span className="text-danger">*</span>}
              </Form.Label>
              <Form.Control
                type="text"
                required={!isEdit}
                readOnly={isEdit}
                disabled={isEdit}
                maxLength={50}
                placeholder="Ví dụ: PROJECTOR, AIR_CONDITIONER, SOUND_SYSTEM"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                isInvalid={Boolean(errors.code)}
              />
              <Form.Control.Feedback type="invalid">
                {errors.code}
              </Form.Control.Feedback>
              <Form.Text className="text-muted">
                {isEdit
                  ? 'Mã loại thiết bị là cố định và không thể thay đổi sau khi tạo.'
                  : 'Mã định danh duy nhất (tối đa 50 ký tự, viết hoa, gạch dưới).'}
              </Form.Text>
            </Form.Group>

            {/* Name */}
            <Form.Group as={Col} xs={12} controlId="equipment-type-name">
              <Form.Label>
                Tên loại thiết bị <span className="text-danger">*</span>
              </Form.Label>
              <Form.Control
                type="text"
                required
                maxLength={150}
                placeholder="Ví dụ: Máy chiếu, Điều hòa nhiệt độ, Hệ thống âm thanh"
                value={name}
                onChange={(e) => setName(e.target.value)}
                isInvalid={Boolean(errors.name)}
              />
              <Form.Control.Feedback type="invalid">
                {errors.name}
              </Form.Control.Feedback>
            </Form.Group>

            {/* Description */}
            <Form.Group as={Col} xs={12} controlId="equipment-type-description">
              <Form.Label>Mô tả (tùy chọn)</Form.Label>
              <Form.Control
                as="textarea"
                rows={3}
                maxLength={500}
                placeholder="Mô tả công dụng hoặc phạm vi của loại thiết bị này..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                isInvalid={Boolean(errors.description)}
              />
              <Form.Control.Feedback type="invalid">
                {errors.description}
              </Form.Control.Feedback>
            </Form.Group>

            {/* Active Switch (Edit mode) */}
            {isEdit && (
              <Col xs={12}>
                <Form.Check
                  type="switch"
                  id="equipment-type-active"
                  label="Loại thiết bị đang hoạt động"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                />
                <Form.Text className="text-muted">
                  Khi tắt, loại thiết bị sẽ bị ẩn và không thể chọn khi gán cho tài sản mới.
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
            'Tạo loại thiết bị'
          )}
        </Button>
      </Modal.Footer>
    </Form>
  )
}

export function EquipmentTypeModal({
  show,
  onHide,
  equipmentTypeToEdit,
  onSuccessNotice,
}) {
  const isEdit = Boolean(equipmentTypeToEdit?.id)

  return (
    <Modal show={show} onHide={onHide} backdrop="static">
      <Modal.Header closeButton>
        <Modal.Title as="h2" className="h5 mb-0">
          {isEdit ? `Sửa loại thiết bị: ${equipmentTypeToEdit.name}` : 'Thêm loại thiết bị mới'}
        </Modal.Title>
      </Modal.Header>

      {show && (
        <EquipmentTypeModalForm
          key={equipmentTypeToEdit?.id ?? 'create'}
          equipmentTypeToEdit={equipmentTypeToEdit}
          onHide={onHide}
          onSuccessNotice={onSuccessNotice}
        />
      )}
    </Modal>
  )
}
