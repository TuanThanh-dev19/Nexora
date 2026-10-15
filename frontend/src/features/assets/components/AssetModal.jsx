import { Modal } from 'react-bootstrap'
import { AssetForm } from './AssetForm'

export function AssetModal({
  show,
  onHide,
  assetToEdit = null,
  onSuccessNotice,
}) {
  const isEdit = Boolean(assetToEdit?.id)

  return (
    <Modal show={show} onHide={onHide} backdrop="static" size="lg">
      <Modal.Header closeButton>
        <Modal.Title as="h2" className="h5 mb-0">
          {isEdit
            ? `Chỉnh sửa tài sản: ${assetToEdit.assetCode}`
            : 'Thêm tài sản mới'}
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        {show && (
          <AssetForm
            key={`${assetToEdit?.id ?? 'create'}-${assetToEdit?.version ?? 0}`}
            assetToEdit={assetToEdit}
            onCancel={onHide}
            onSuccess={(savedAsset) => {
              onHide()
              if (onSuccessNotice) {
                onSuccessNotice(
                  isEdit
                    ? `Đã cập nhật tài sản "${savedAsset.assetCode}" thành công.`
                    : `Đã tạo tài sản "${savedAsset.assetCode}" thành công.`,
                )
              }
            }}
          />
        )}
      </Modal.Body>
    </Modal>
  )
}
