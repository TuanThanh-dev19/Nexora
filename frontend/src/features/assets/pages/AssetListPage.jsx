import { useState } from 'react'
import { Alert, Button, Card, Container, Stack } from 'react-bootstrap'
import { useLocation } from 'react-router'
import { FiBox, FiPlus } from 'react-icons/fi'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { AssetFilters } from '../components/AssetFilters'
import { AssetTable } from '../components/AssetTable'
import { AssetModal } from '../components/AssetModal'
import { AssetStatusModal } from '../components/AssetStatusModal'
import { useAssets } from '../hooks/useAssets'
import { useAssetSearchParams } from '../hooks/useAssetSearchParams'
import { formatAssetError } from '../api/assetError'

export function AssetListPage() {
  const location = useLocation()
  const { params, requestParams, update, reset } = useAssetSearchParams()
  const query = useAssets(requestParams)

  // Notification state
  const [notice, setNotice] = useState(null)

  // Modals state
  const [showAssetModal, setShowAssetModal] = useState(false)
  const [assetToEdit, setAssetToEdit] = useState(null)
  const [statusModalAsset, setStatusModalAsset] = useState(null)

  const handleOpenCreate = () => {
    setAssetToEdit(null)
    setShowAssetModal(true)
  }

  const handleOpenEdit = (asset) => {
    setAssetToEdit(asset)
    setShowAssetModal(true)
  }

  const handleOpenStatus = (asset) => {
    setStatusModalAsset(asset)
  }

  return (
    <Container className="page-container">
      <div className="page-heading">
        <div>
          <p className="eyebrow">QUẢN LÝ THIẾT BỊ</p>
          <h1 className="h2">Danh sách tài sản</h1>
          <p className="text-secondary mb-0">
            Quản trị thông tin thiết bị, vị trí và vòng đời trạng thái hoạt
            động.
          </p>
        </div>
        <Button onClick={handleOpenCreate}>
          <FiPlus aria-hidden="true" /> Thêm tài sản
        </Button>
        <span className="page-heading-icon">
          <FiBox aria-hidden="true" />
        </span>
      </div>

      {notice && (
        <Alert
          variant="success"
          dismissible
          onClose={() => setNotice(null)}
          className="mb-4"
        >
          {notice}
        </Alert>
      )}

      <Card className="ticket-list-card">
        <Card.Body className="ticket-list-body">
          <AssetFilters {...params} onChange={update} onReset={reset} />

          <div className="ticket-results-heading">
            <h2>Kết quả danh sách</h2>
            {!query.isError && query.data && (
              <span className="result-count">
                {query.data.totalElements} tài sản
              </span>
            )}
          </div>

          {query.isError ? (
            <Alert variant="danger">
              <p className="mb-2">
                {formatAssetError(
                  query.error,
                  'Không thể tải danh sách tài sản.',
                )}
              </p>
              <Stack direction="horizontal" gap={2}>
                <Button
                  size="sm"
                  variant="outline-danger"
                  onClick={() => query.refetch()}
                >
                  Thử lại
                </Button>
                <Button size="sm" variant="outline-secondary" onClick={reset}>
                  Đặt lại bộ lọc
                </Button>
              </Stack>
            </Alert>
          ) : query.isPending ? (
            <LoadingSpinner label="Đang tải danh sách tài sản..." />
          ) : query.data?.content?.length === 0 ? (
            <EmptyState message="Không có tài sản phù hợp." />
          ) : (
            <>
              <AssetTable
                assets={query.data.content}
                listSearch={location.search}
                onEdit={handleOpenEdit}
                onChangeStatus={handleOpenStatus}
              />

              <Stack
                direction="horizontal"
                gap={2}
                className="ticket-pagination justify-content-between flex-wrap mt-3"
                aria-label="Phân trang"
              >
                <span>
                  {query.data.totalElements} tài sản · Trang{' '}
                  {query.data.page + 1}/{Math.max(1, query.data.totalPages)}
                </span>
                <Stack direction="horizontal" gap={2}>
                  <Button
                    variant="outline-secondary"
                    disabled={query.data.page === 0}
                    onClick={() => update({ page: query.data.page - 1 })}
                  >
                    Trang trước
                  </Button>
                  {query.data.page >= query.data.totalPages &&
                    query.data.page > 0 && (
                      <Button
                        variant="outline-secondary"
                        onClick={() => update({ page: 0 })}
                      >
                        Trang đầu
                      </Button>
                    )}
                  <Button
                    variant="outline-secondary"
                    disabled={query.data.page + 1 >= query.data.totalPages}
                    onClick={() => update({ page: query.data.page + 1 })}
                  >
                    Trang sau
                  </Button>
                </Stack>
              </Stack>
            </>
          )}
        </Card.Body>
      </Card>

      {/* Create / Edit Modal */}
      <AssetModal
        show={showAssetModal}
        onHide={() => setShowAssetModal(false)}
        assetToEdit={assetToEdit}
        onSuccessNotice={(msg) => setNotice(msg)}
      />

      {/* Change Status Modal */}
      <AssetStatusModal
        show={Boolean(statusModalAsset)}
        onHide={() => setStatusModalAsset(null)}
        asset={statusModalAsset}
        onSuccessNotice={(msg) => setNotice(msg)}
      />
    </Container>
  )
}
