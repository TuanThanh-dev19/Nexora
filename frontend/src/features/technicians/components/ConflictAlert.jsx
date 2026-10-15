import { Alert, Button } from 'react-bootstrap'
import { FiAlertTriangle, FiRotateCcw } from 'react-icons/fi'

export function ConflictAlert({ onReload }) {
  return (
    <Alert
      variant="warning"
      className="d-flex align-items-center justify-content-between gap-3 flex-wrap mb-4 shadow-sm"
      role="alert"
    >
      <div className="d-flex align-items-center gap-2">
        <FiAlertTriangle className="fs-5 text-warning-emphasis flex-shrink-0" aria-hidden="true" />
        <div>
          <strong className="d-block">Xung đột phiên bản (409 Conflict)</strong>
          <span>
            Hồ sơ kỹ thuật viên đã được cập nhật bởi một người dùng hoặc phiên làm việc khác. Vui lòng tải lại dữ liệu mới nhất trước khi tiếp tục thao tác.
          </span>
        </div>
      </div>
      {onReload && (
        <Button
          variant="outline-dark"
          size="sm"
          onClick={onReload}
          className="d-inline-flex align-items-center gap-1 text-nowrap"
        >
          <FiRotateCcw aria-hidden="true" /> Tải lại dữ liệu
        </Button>
      )}
    </Alert>
  )
}
