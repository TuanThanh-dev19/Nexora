import { Alert, Button } from 'react-bootstrap'
import { FiAlertCircle } from 'react-icons/fi'

export function TicketErrorState({ error, onRetry, onReset }) {
  const status = error?.response?.status
  const message =
    status === 400
      ? 'Tham số không hợp lệ. Vui lòng kiểm tra bộ lọc.'
      : status === 401
        ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
        : status === 403 || status === 404
          ? 'Không thể truy cập phiếu yêu cầu này.'
          : status == null
            ? 'Không thể kết nối máy chủ. Vui lòng thử lại.'
            : 'Không thể tải dữ liệu lúc này. Vui lòng thử lại.'
  return (
    <Alert variant="danger" role="alert" className="ticket-error-state">
      <FiAlertCircle aria-hidden="true" className="error-state-icon" />
      <p className="mb-2">{message}</p>
      {status === 400 && onReset && (
        <Button variant="outline-danger" onClick={onReset}>
          Đặt lại bộ lọc
        </Button>
      )}
      {(status == null || status >= 500) && onRetry && (
        <Button variant="outline-danger" onClick={onRetry}>
          Thử lại
        </Button>
      )}
    </Alert>
  )
}
