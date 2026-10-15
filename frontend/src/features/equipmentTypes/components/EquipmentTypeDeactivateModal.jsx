import { useState } from 'react'
import { Alert, Button, Modal, Spinner } from 'react-bootstrap'
import { FiAlertTriangle, FiCheckCircle } from 'react-icons/fi'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { equipmentTypeApi } from '../api/equipmentTypeApi'
import { equipmentTypeKeys } from '../hooks/useEquipmentTypes'
import { formatEquipmentTypeError } from '../api/equipmentTypeError'

export function EquipmentTypeDeactivateModal({
  show,
  onHide,
  equipmentType,
  targetActive, // false for deactivation, true for reactivation
  onSuccessNotice,
}) {
  const queryClient = useQueryClient()
  const [error, setError] = useState(null)

  const isDeactivating = targetActive === false

  const mutation = useMutation({
    mutationFn: async () => {
      if (!equipmentType) return
      return equipmentTypeApi.update(equipmentType.id, {
        name: equipmentType.name,
        description: equipmentType.description,
        active: targetActive,
      })
    },
    onSuccess: async (updatedType) => {
      await queryClient.invalidateQueries({ queryKey: equipmentTypeKeys.all })
      onHide()
      onSuccessNotice(
        isDeactivating
          ? `Đã ngừng hoạt động loại thiết bị "${updatedType.name}" (${updatedType.code}).`
          : `Đã kích hoạt lại loại thiết bị "${updatedType.name}" (${updatedType.code}).`,
      )
    },
    onError: (err) => {
      setError(formatEquipmentTypeError(err))
    },
  })

  const handleConfirm = () => {
    setError(null)
    mutation.mutate()
  }

  if (!equipmentType) return null

  return (
    <Modal show={show} onHide={mutation.isPending ? undefined : onHide} backdrop="static">
      <Modal.Header closeButton={!mutation.isPending}>
        <Modal.Title as="h2" className="h5 d-flex align-items-center gap-2 mb-0">
          {isDeactivating ? (
            <>
              <FiAlertTriangle className="text-warning" aria-hidden="true" />
              <span>Xác nhận ngừng hoạt động</span>
            </>
          ) : (
            <>
              <FiCheckCircle className="text-success" aria-hidden="true" />
              <span>Kích hoạt lại loại thiết bị</span>
            </>
          )}
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        {error && (
          <Alert variant="danger" dismissible onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {isDeactivating ? (
          <div>
            <p>
              Bạn có chắc chắn muốn ngừng hoạt động loại thiết bị{' '}
              <strong>{equipmentType.name}</strong> (<code>{equipmentType.code}</code>)?
            </p>
            <div className="alert alert-warning small mb-0">
              <strong>Lưu ý:</strong>
              <ul className="mb-0 ps-3 mt-1">
                <li>
                  Thao tác này cập nhật trạng thái hoạt động thành ngừng sử dụng,
                  không xóa dữ liệu khỏi hệ thống.
                </li>
                <li>
                  Các tài sản và phiếu yêu cầu đã liên kết trước đó vẫn giữ nguyên dữ liệu tham chiếu.
                </li>
                <li>
                  Loại thiết bị này sẽ không thể chọn khi tạo hoặc gán cho tài sản mới.
                </li>
              </ul>
            </div>
          </div>
        ) : (
          <p className="mb-0">
            Bạn có muốn kích hoạt lại loại thiết bị <strong>{equipmentType.name}</strong> (
            <code>{equipmentType.code}</code>) để cho phép gán cho các tài sản mới?
          </p>
        )}
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
          variant={isDeactivating ? 'danger' : 'success'}
          disabled={mutation.isPending}
          onClick={handleConfirm}
        >
          {mutation.isPending ? (
            <>
              <Spinner animation="border" size="sm" className="me-2" />
              Đang xử lý…
            </>
          ) : isDeactivating ? (
            'Xác nhận ngừng hoạt động'
          ) : (
            'Xác nhận kích hoạt lại'
          )}
        </Button>
      </Modal.Footer>
    </Modal>
  )
}
