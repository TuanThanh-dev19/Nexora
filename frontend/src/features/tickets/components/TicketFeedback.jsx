import { useState } from 'react'
import { Alert, Button, Card, Form } from 'react-bootstrap'
import { useAuth } from '../../auth/context/useAuth'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { useTicketFeedback } from '../hooks/useTicketCollaboration'
import {
  canSubmitFeedback,
  collaborationErrorMessage,
} from '../constants/ticketCollaboration'
import {
  FEEDBACK_COMMENT_MAX_LENGTH,
  validateFeedback,
} from '../schemas/ticketCollaborationValidation'
import { formatTicketTime } from '../utils/ticketFormatting'

export function TicketFeedback({ ticket }) {
  const { user } = useAuth()
  const { query, mutation, available } = useTicketFeedback(ticket)
  const [rating, setRating] = useState(null)
  const [comment, setComment] = useState('')
  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState('')
  const conflict = mutation.error?.response?.status === 409
  const fieldErrors = {
    ...mutation.error?.response?.data?.fieldErrors,
    ...errors,
  }
  const submit = async (event) => {
    event.preventDefault()
    if (mutation.isPending || conflict || query.isFetching) return
    const payload = {
      rating,
      ...(comment.trim() && { comment: comment.trim() }),
    }
    const next = validateFeedback(payload)
    setErrors(next)
    if (Object.keys(next).length) return
    try {
      await mutation.mutateAsync(payload)
      setRating(null)
      setComment('')
      setNotice('Đã gửi đánh giá trong bản mô phỏng, chưa lưu lên máy chủ.')
    } catch {
      /* Preserve inputs; duplicate feedback is refreshed by the hook. */
    }
  }
  return (
    <Card className="ticket-section mb-3">
      <Card.Body>
        <h2 className="h5">Đánh giá</h2>
        {!available ? (
          <Alert variant="info">
            Đánh giá chưa khả dụng. API sẽ được bổ sung ở milestone sau.
          </Alert>
        ) : (
          <>
            <p className="text-secondary small">
              Bản mô phỏng: đánh giá chưa được lưu lên máy chủ.
            </p>
            {query.isPending && <LoadingSpinner label="Đang tải đánh giá..." />}
            {query.isError && (
              <Alert variant="danger">
                <p>{collaborationErrorMessage(query.error)}</p>
                <Button
                  variant="outline-danger"
                  onClick={() => query.refetch()}
                >
                  Thử lại đánh giá
                </Button>
              </Alert>
            )}
            {notice && (
              <Alert variant="success" role="status">
                {notice}
              </Alert>
            )}
            {mutation.isError && (
              <Alert variant={conflict ? 'warning' : 'danger'}>
                <p>{collaborationErrorMessage(mutation.error)}</p>
                {conflict && (
                  <Button
                    variant="outline-dark"
                    onClick={async () => {
                      await query.refetch()
                      mutation.reset()
                    }}
                  >
                    Tải lại đánh giá
                  </Button>
                )}
              </Alert>
            )}
            {query.isSuccess &&
              (query.data ? (
                <div>
                  <p className="fw-semibold">Đánh giá: {query.data.rating}/5</p>
                  {query.data.comment && (
                    <p
                      className="text-break"
                      style={{ whiteSpace: 'pre-wrap' }}
                    >
                      {query.data.comment}
                    </p>
                  )}
                  <p className="small text-secondary">
                    {formatTicketTime(query.data.createdAt)} · Đánh giá chỉ được
                    gửi một lần và không thể sửa.
                  </p>
                </div>
              ) : canSubmitFeedback(ticket, user) ? (
                <Form noValidate onSubmit={submit}>
                  <fieldset
                    disabled={
                      mutation.isPending || query.isFetching || conflict
                    }
                  >
                    <fieldset
                      className="mb-3"
                      aria-describedby={
                        fieldErrors.rating
                          ? 'ticket-feedback-rating-error'
                          : undefined
                      }
                    >
                      <legend className="fs-6">Mức đánh giá (1–5)</legend>
                      {[1, 2, 3, 4, 5].map((value) => (
                        <Form.Check
                          key={value}
                          inline
                          type="radio"
                          id={`ticket-rating-${value}`}
                          name="ticket-rating"
                          label={`${value} sao`}
                          value={value}
                          checked={rating === value}
                          isInvalid={Boolean(fieldErrors.rating)}
                          onChange={() => {
                            setRating(value)
                            setErrors({})
                            mutation.reset()
                          }}
                        />
                      ))}
                      {fieldErrors.rating && (
                        <p
                          id="ticket-feedback-rating-error"
                          className="text-danger"
                          role="alert"
                        >
                          {fieldErrors.rating}
                        </p>
                      )}
                    </fieldset>
                    <Form.Group
                      controlId="ticket-feedback-comment"
                      className="mb-3"
                    >
                      <Form.Label>Nhận xét (không bắt buộc)</Form.Label>
                      <Form.Control
                        as="textarea"
                        rows={3}
                        maxLength={FEEDBACK_COMMENT_MAX_LENGTH}
                        value={comment}
                        isInvalid={Boolean(fieldErrors.comment)}
                        onChange={(event) => {
                          setComment(event.target.value)
                          setErrors({})
                          mutation.reset()
                        }}
                      />
                      <Form.Control.Feedback type="invalid">
                        {fieldErrors.comment}
                      </Form.Control.Feedback>
                    </Form.Group>
                    <Button type="submit">
                      {mutation.isPending ? 'Đang gửi…' : 'Gửi đánh giá'}
                    </Button>
                  </fieldset>
                </Form>
              ) : (
                <p className="text-secondary">
                  Chưa có đánh giá. Chỉ người báo có thể gửi đánh giá khi phiếu
                  đã đóng.
                </p>
              ))}
          </>
        )}
      </Card.Body>
    </Card>
  )
}
