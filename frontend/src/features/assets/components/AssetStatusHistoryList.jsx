import { Alert, Button, Card } from 'react-bootstrap'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { ASSET_STATUS_LABELS } from '../constants/assetStatuses'
import { formatTicketTime } from '../../tickets/utils/ticketFormatting'
import { useAssetHistory } from '../hooks/useAssets'
import { formatAssetError } from '../api/assetError'

export function AssetStatusHistoryList({ assetId }) {
  const {
    data: history,
    isPending,
    isError,
    error,
    refetch,
  } = useAssetHistory(assetId)

  return (
    <Card className="ticket-section mb-3">
      <Card.Body>
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h2 className="h5 mb-0">Lịch sử trạng thái</h2>
          {!isPending && !isError && history && (
            <span className="small text-secondary">
              {history.length} lần thay đổi
            </span>
          )}
        </div>

        {isPending ? (
          <LoadingSpinner label="Đang tải lịch sử trạng thái..." />
        ) : isError ? (
          <Alert variant="danger">
            <p className="mb-2">
              {formatAssetError(error, 'Không thể tải lịch sử trạng thái.')}
            </p>
            <Button
              size="sm"
              variant="outline-danger"
              onClick={() => refetch()}
            >
              Thử lại
            </Button>
          </Alert>
        ) : !history || history.length === 0 ? (
          <EmptyState message="Chưa có lịch sử trạng thái." />
        ) : (
          <ol className="mb-0 ticket-timeline">
            {history.map((entry) => {
              const fromLabel = entry.fromStatus
                ? (ASSET_STATUS_LABELS[entry.fromStatus] ?? entry.fromStatus)
                : 'Khởi tạo tài sản'
              const toLabel =
                ASSET_STATUS_LABELS[entry.toStatus] ?? entry.toStatus

              return (
                <li key={entry.id}>
                  <div className="fw-semibold">
                    {fromLabel} → {toLabel}
                  </div>
                  <div className="small text-secondary">
                    {entry.changedBy?.fullName ??
                      entry.changedBy?.email ??
                      'Hệ thống'}{' '}
                    · {formatTicketTime(entry.changedAt)}
                    {entry.ticketId && ` · Phiếu #${entry.ticketId}`}
                  </div>
                  {entry.reason && (
                    <p
                      className="mb-0 mt-1 text-break"
                      style={{ whiteSpace: 'pre-wrap' }}
                    >
                      {entry.reason}
                    </p>
                  )}
                </li>
              )
            })}
          </ol>
        )}
      </Card.Body>
    </Card>
  )
}
