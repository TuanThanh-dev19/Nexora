import { useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Container,
  Row,
  Stack,
} from 'react-bootstrap'
import { Link, useLocation, useParams } from 'react-router'
import { FiEdit2, FiRefreshCw } from 'react-icons/fi'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { AssetStatusBadge } from '../components/AssetStatusBadge'
import { AssetStatusHistoryList } from '../components/AssetStatusHistoryList'
import { AssetModal } from '../components/AssetModal'
import { AssetStatusModal } from '../components/AssetStatusModal'
import { useAsset } from '../hooks/useAssets'
import { isPositiveId } from '../hooks/useAssetSearchParams'
import { formatAssetError } from '../api/assetError'
import { formatTicketTime } from '../../tickets/utils/ticketFormatting'

export function AssetDetailPage() {
  const { assetId } = useParams()
  const location = useLocation()

  const listSearch = location.state?.listSearch
  const back = `/assets${typeof listSearch === 'string' && listSearch.startsWith('?') ? listSearch : ''}`

  const isValidId = isPositiveId(assetId)
  const query = useAsset(assetId, isValidId)
  const asset = query.data

  const [notice, setNotice] = useState(null)
  const [showEditModal, setShowEditModal] = useState(false)
  const [showStatusModal, setShowStatusModal] = useState(false)

  return (
    <Container className="page-container">
      {notice && (
        <Alert
          variant="success"
          dismissible
          onClose={() => setNotice(null)}
          className="mb-3"
        >
          {notice}
        </Alert>
      )}

      <Link to={back} className="back-link">
        ← Danh sách tài sản
      </Link>

      <div className="page-heading">
        <div>
          <p className="eyebrow">QUẢN LÝ THIẾT BỊ</p>
          <h1 className="h2 mb-0">Chi tiết tài sản</h1>
        </div>
        {asset && (
          <Stack direction="horizontal" gap={2}>
            <Button
              variant="outline-primary"
              onClick={() => setShowEditModal(true)}
            >
              <FiEdit2 aria-hidden="true" /> Chỉnh sửa tài sản
            </Button>
            <Button
              variant="outline-warning"
              onClick={() => setShowStatusModal(true)}
            >
              <FiRefreshCw aria-hidden="true" /> Đổi trạng thái
            </Button>
          </Stack>
        )}
      </div>

      {!isValidId ? (
        <Alert variant="danger">
          Mã định danh tài sản không hợp lệ. Vui lòng quay lại danh sách.
        </Alert>
      ) : query.isError ? (
        <Alert variant="danger">
          <p className="mb-2">
            {formatAssetError(
              query.error,
              'Không thể tải thông tin chi tiết tài sản.',
            )}
          </p>
          <Button
            size="sm"
            variant="outline-danger"
            onClick={() => query.refetch()}
          >
            Thử lại
          </Button>
        </Alert>
      ) : query.isPending ? (
        <LoadingSpinner label="Đang tải chi tiết tài sản..." />
      ) : (
        asset && (
          <>
            <Card className="ticket-summary mb-4">
              <Card.Body>
                <Stack
                  direction="horizontal"
                  gap={2}
                  className="flex-wrap mb-3 align-items-center"
                >
                  <h2 className="h4 mb-0 ticket-number">{asset.assetCode}</h2>
                  <AssetStatusBadge status={asset.status} />
                  <span className="badge bg-light text-dark border">
                    Phiên bản {asset.version}
                  </span>
                </Stack>

                <Row as="dl" className="mb-0 ticket-metadata">
                  {[
                    [
                      'Loại thiết bị',
                      asset.equipmentType
                        ? `${asset.equipmentType.name} (${asset.equipmentType.code})`
                        : '—',
                    ],
                    [
                      'Địa điểm',
                      asset.location
                        ? `${asset.location.name} (${asset.location.code})`
                        : '—',
                    ],
                    ['Số sê-ri', asset.serialNumber || '—'],
                    ['Ngày mua', asset.purchasedAt || '—'],
                    ['Hạn bảo hành', asset.warrantyUntil || '—'],
                    ['Ngày tạo', formatTicketTime(asset.createdAt)],
                    ['Cập nhật lần cuối', formatTicketTime(asset.updatedAt)],
                  ].map(([label, value]) => (
                    <Col xs={12} sm={6} lg={3} key={label} className="mt-2">
                      <dt className="text-secondary small">{label}</dt>
                      <dd className="fw-semibold mb-0">{value}</dd>
                    </Col>
                  ))}
                </Row>
              </Card.Body>
            </Card>

            <div className="ticket-detail-grid">
              <div>
                <Card className="ticket-section mb-3">
                  <Card.Body>
                    <h2 className="h5">Mô tả tài sản</h2>
                    <p
                      className="mb-0 text-break"
                      style={{ whiteSpace: 'pre-wrap' }}
                    >
                      {asset.description || (
                        <span className="text-secondary">
                          Chưa có thông tin mô tả cho tài sản này.
                        </span>
                      )}
                    </p>
                  </Card.Body>
                </Card>
              </div>

              <div>
                <AssetStatusHistoryList assetId={asset.id} />
              </div>
            </div>

            {/* Edit Modal */}
            <AssetModal
              show={showEditModal}
              onHide={() => setShowEditModal(false)}
              assetToEdit={asset}
              onSuccessNotice={(msg) => setNotice(msg)}
            />

            {/* Status Modal */}
            <AssetStatusModal
              show={showStatusModal}
              onHide={() => setShowStatusModal(false)}
              asset={asset}
              onSuccessNotice={(msg) => setNotice(msg)}
            />
          </>
        )
      )}
    </Container>
  )
}
