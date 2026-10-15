import { useState } from 'react'
import { Alert, Button, Form, Modal, Spinner } from 'react-bootstrap'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ASSET_STATUS_CODES,
  ASSET_STATUS_LABELS,
} from '../constants/assetStatuses'
import { AssetStatusBadge } from './AssetStatusBadge'
import { assetApi } from '../api/assetApi'
import { assetKeys } from '../hooks/useAssets'
import {
  extractFieldErrors,
  formatAssetError,
  isConflictError,
} from '../api/assetError'

export function AssetStatusModal({ show, onHide, asset, onSuccessNotice }) {
  return (
    <Modal show={show} onHide={onHide} backdrop="static">
      <Modal.Header closeButton>
        <Modal.Title as="h2" className="h5 mb-0">
          Chuyển trạng thái tài sản
        </Modal.Title>
      </Modal.Header>

      {show && asset && (
        <AssetStatusModalForm
          key={`${asset.id}-${asset.version}`}
          asset={asset}
          onHide={onHide}
          onSuccessNotice={onSuccessNotice}
        />
      )}
    </Modal>
  )
}

function AssetStatusModalForm({ asset, onHide, onSuccessNotice }) {
  const queryClient = useQueryClient()

  // Track local asset object (which can be refreshed on 409)
  const [currentAsset, setCurrentAsset] = useState(asset)

  // Target status options excluding current status
  const availableStatuses = Object.values(ASSET_STATUS_CODES).filter(
    (code) => code !== currentAsset.status,
  )

  const [targetStatus, setTargetStatus] = useState(
    () => availableStatuses[0] ?? '',
  )
  const [reason, setReason] = useState('')

  const [errors, setErrors] = useState({})
  const [serverError, setServerError] = useState(null)
  const [conflictOccurred, setConflictOccurred] = useState(false)
  const [isReloading, setIsReloading] = useState(false)

  // Reload latest data on 409 conflict
  const handleReloadLatest = async () => {
    setIsReloading(true)
    setServerError(null)
    setConflictOccurred(false)
    try {
      const fresh = await assetApi.getById(currentAsset.id)
      setCurrentAsset(fresh)
      // Pick first valid status for the refreshed asset
      const nextAvailable = Object.values(ASSET_STATUS_CODES).filter(
        (code) => code !== fresh.status,
      )
      setTargetStatus(nextAvailable[0] ?? '')
      setErrors({})
    } catch (err) {
      setServerError(
        formatAssetError(err, 'Không thể tải lại tài sản mới nhất.'),
      )
    } finally {
      setIsReloading(false)
    }
  }

  const mutation = useMutation({
    mutationFn: async () => {
      return assetApi.changeStatus(currentAsset.id, {
        targetStatus,
        reason: reason.trim(),
        version: currentAsset.version,
      })
    },
    onSuccess: async (updatedAsset) => {
      await queryClient.invalidateQueries({ queryKey: assetKeys.all })
      onHide()
      onSuccessNotice(
        `Đã chuyển trạng thái tài sản "${updatedAsset.assetCode}" sang "${ASSET_STATUS_LABELS[updatedAsset.status] ?? updatedAsset.status}".`,
      )
    },
    onError: (err) => {
      if (isConflictError(err)) {
        setConflictOccurred(true)
        setServerError(
          'Xung đột phiên bản: Trạng thái hoặc dữ liệu tài sản đã bị thay đổi bởi phiên làm việc khác. Vui lòng bấm "Tải lại dữ liệu mới nhất" để cập nhật thông tin trước khi thực hiện.',
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

  const validate = () => {
    const newErrors = {}

    if (!targetStatus) {
      newErrors.targetStatus = 'Vui lòng chọn trạng thái mới.'
    } else if (targetStatus === currentAsset.status) {
      newErrors.targetStatus =
        'Trạng thái đích không được trùng với trạng thái hiện tại.'
    }

    if (!reason || !reason.trim()) {
      newErrors.reason = 'Lý do chuyển trạng thái là bắt buộc.'
    } else if (reason.trim().length > 500) {
      newErrors.reason = 'Lý do không được vượt quá 500 ký tự.'
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
          <Alert
            variant={conflictOccurred ? 'warning' : 'danger'}
            dismissible={!conflictOccurred}
            onClose={() => setServerError(null)}
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
          <div className="mb-3">
            <span className="text-secondary small d-block mb-1">
              Mã tài sản:
            </span>
            <strong>{currentAsset.assetCode}</strong>
          </div>

          <div className="mb-3">
            <span className="text-secondary small d-block mb-1">
              Trạng thái hiện tại:
            </span>
            <AssetStatusBadge status={currentAsset.status} />
          </div>

          <Form.Group className="mb-3" controlId="target-status">
            <Form.Label>
              Trạng thái chuyển đổi <span className="text-danger">*</span>
            </Form.Label>
            <Form.Select
              value={targetStatus}
              onChange={(e) => setTargetStatus(e.target.value)}
              isInvalid={Boolean(errors.targetStatus)}
            >
              {availableStatuses.map((code) => (
                <option key={code} value={code}>
                  {ASSET_STATUS_LABELS[code] ?? code} ({code})
                </option>
              ))}
            </Form.Select>
            <Form.Control.Feedback type="invalid">
              {errors.targetStatus}
            </Form.Control.Feedback>
          </Form.Group>

          <Form.Group className="mb-3" controlId="status-reason">
            <Form.Label>
              Lý do thay đổi <span className="text-danger">*</span>
            </Form.Label>
            <Form.Control
              as="textarea"
              rows={3}
              maxLength={500}
              placeholder="Nhập lý do cụ thể chuyển trạng thái tài sản (bắt buộc)..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              isInvalid={Boolean(errors.reason)}
            />
            <Form.Control.Feedback type="invalid">
              {errors.reason}
            </Form.Control.Feedback>
            <Form.Text className="text-muted">
              Tối đa 500 ký tự. Không được để trống.
            </Form.Text>
          </Form.Group>
        </fieldset>
      </Modal.Body>

      <Modal.Footer>
        <Button
          variant="secondary"
          disabled={mutation.isPending || isReloading}
          onClick={onHide}
        >
          Hủy
        </Button>
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
            'Xác nhận đổi trạng thái'
          )}
        </Button>
      </Modal.Footer>
    </Form>
  )
}
