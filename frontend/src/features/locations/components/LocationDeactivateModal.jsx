import { useState } from 'react'
import { Alert, Button, Modal, Spinner } from 'react-bootstrap'
import { FiAlertTriangle, FiCheckCircle } from 'react-icons/fi'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { locationApi } from '../api/locationApi'
import { locationKeys } from '../hooks/useLocations'
import { formatLocationError } from '../api/locationError'

export function LocationDeactivateModal({
  show,
  onHide,
  location,
  targetActive, // false for deactivation, true for reactivation
  onSuccessNotice,
}) {
  const queryClient = useQueryClient()
  const [error, setError] = useState(null)

  const isDeactivating = targetActive === false

  const mutation = useMutation({
    mutationFn: async () => {
      if (!location) return
      return locationApi.update(location.id, {
        name: location.name,
        parentId: location.parentId,
        description: location.description,
        active: targetActive,
      })
    },
    onSuccess: async (updatedLocation) => {
      await queryClient.invalidateQueries({ queryKey: locationKeys.all })
      onHide()
      onSuccessNotice(
        isDeactivating
          ? `Đã ngừng hoạt động địa điểm "${updatedLocation.name}" (${updatedLocation.code}).`
          : `Đã kích hoạt lại địa điểm "${updatedLocation.name}" (${updatedLocation.code}).`,
      )
    },
    onError: (err) => {
      setError(formatLocationError(err))
    },
  })

  const handleConfirm = () => {
    setError(null)
    mutation.mutate()
  }

  if (!location) return null

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
              <span>Kích hoạt lại địa điểm</span>
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
              Bạn có chắc chắn muốn ngừng hoạt động địa điểm{' '}
              <strong>{location.name}</strong> (<code>{location.code}</code>)?
            </p>
            <div className="alert alert-warning small mb-0">
              <strong>Lưu ý:</strong>
              <ul className="mb-0 ps-3 mt-1">
                <li>
                  Thao tác này sử dụng cơ chế cập nhật trạng thái (soft-delete),
                  không xóa vĩnh viễn dữ liệu khỏi hệ thống.
                </li>
                <li>
                  Các phiếu yêu cầu và tài sản đã liên kết trước đây vẫn được giữ nguyên.
                </li>
                <li>
                  Địa điểm sẽ bị ẩn khỏi các bộ chọn khi tạo phiếu yêu cầu hoặc tài sản mới.
                </li>
              </ul>
            </div>
          </div>
        ) : (
          <p className="mb-0">
            Bạn có muốn kích hoạt lại địa điểm <strong>{location.name}</strong> (
            <code>{location.code}</code>) để cho phép tiếp tục sử dụng trong hệ thống?
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
