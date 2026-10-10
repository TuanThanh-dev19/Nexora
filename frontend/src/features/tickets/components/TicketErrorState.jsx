import { Alert, Button } from 'react-bootstrap'
import { FiAlertCircle } from 'react-icons/fi'

function errorMessage(status) {
  switch (status) {
    case 400:
      return 'Tham số không hợp lệ. Vui lòng kiểm tra bộ lọc.'
    case 401:
      return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    case 403:
    case 404:
      return 'Không thể truy cập phiếu yêu cầu này.'
    default:
      if (status == null) return 'Không thể kết nối máy chủ. Vui lòng thử lại.'
      return 'Không thể tải dữ liệu lúc này. Vui lòng thử lại.'
  }
}

export function TicketErrorState({
  error,
  status = error?.response?.status,
  onRetry,
  onReset,
}) {
  const message = errorMessage(status)
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
