import { useState } from 'react'
import { Alert, Badge, Button, Card, Form, Stack } from 'react-bootstrap'
import { useAuth } from '../../auth/context/useAuth'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { useTicketComments } from '../hooks/useTicketCollaboration'
import {
  canCreateComment,
  canEditComment,
  collaborationErrorMessage,
} from '../constants/ticketCollaboration'
import {
  COMMENT_MAX_LENGTH,
  validateComment,
} from '../schemas/ticketCollaborationValidation'
import { formatTicketTime } from '../utils/ticketFormatting'

export function TicketComments({ ticket }) {
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const { query, mutation, available } = useTicketComments(ticket, page)
  const [editing, setEditing] = useState(null)
  const [content, setContent] = useState('')
  const [visibility, setVisibility] = useState('PUBLIC')
  const canCreateInternal = canCreateComment(ticket, user, 'INTERNAL')
  const effectiveVisibility = canCreateInternal ? visibility : 'PUBLIC'
  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState('')
  const conflict = mutation.error?.response?.status === 409
  const fieldErrors = {
    ...mutation.error?.response?.data?.fieldErrors,
    ...errors,
  }
  const resetDraft = () => {
    setEditing(null)
    setContent('')
    setVisibility('PUBLIC')
    setErrors({})
    mutation.reset()
  }
  const submit = async (event) => {
    event.preventDefault()
    if (mutation.isPending || conflict) return
    const payload = editing
      ? { content: content.trim() }
      : { content: content.trim(), visibility: effectiveVisibility }
    const next = validateComment(payload, Boolean(editing))
    setErrors(next)
    setNotice('')
    if (Object.keys(next).length) return
    try {
      await mutation.mutateAsync({ payload, comment: editing })
      resetDraft()
      setNotice('Đã lưu bình luận trong bản mô phỏng, chưa lưu lên máy chủ.')
    } catch {
      /* Keep the draft and show mutation feedback. */
    }
  }
  return (
    <Card className="ticket-section mb-3">
      <Card.Body>
        <h2 className="h5">Bình luận</h2>
        {!available ? (
          <Alert variant="info">
            Bình luận chưa khả dụng. API sẽ được bổ sung ở milestone sau.
          </Alert>
        ) : (
          <>
            <p className="text-secondary small">
              Bản mô phỏng: bình luận chưa được lưu lên máy chủ.
            </p>
            {query.isPending && (
              <LoadingSpinner label="Đang tải bình luận..." />
            )}
            {query.isError && (
              <Alert variant="danger">
                <p>{collaborationErrorMessage(query.error)}</p>
                <Button
                  variant="outline-danger"
                  onClick={() => query.refetch()}
                >
                  Thử lại bình luận
                </Button>
              </Alert>
            )}
            {query.isSuccess && (
              <>
                {!query.data.content.length ? (
                  <EmptyState message="Chưa có bình luận." />
                ) : (
                  <ol className="list-unstyled">
                    {query.data.content.map((comment) => (
                      <li key={comment.id} className="border-bottom py-3">
                        <Stack
                          direction="horizontal"
                          gap={2}
                          className="flex-wrap"
                        >
                          <strong>{comment.author.fullName}</strong>
                          <Badge
                            bg={
                              comment.visibility === 'INTERNAL'
                                ? 'secondary'
                                : 'primary'
                            }
                          >
                            {comment.visibility === 'INTERNAL'
                              ? 'Nội bộ'
                              : 'Công khai'}
                          </Badge>
                        </Stack>
                        <p className="small text-secondary mb-1">
                          {formatTicketTime(comment.createdAt)}
                          {comment.editedAt &&
                            ` · Đã sửa ${formatTicketTime(comment.editedAt)}`}
                        </p>
                        <p
                          className="text-break mb-1"
                          style={{ whiteSpace: 'pre-wrap' }}
                        >
                          {comment.content}
                        </p>
                        {canEditComment(ticket, user, comment) && (
                          <Button
                            variant="link"
                            className="p-0"
                            disabled={mutation.isPending || conflict}
                            aria-label={`Sửa bình luận của ${comment.author.fullName}`}
                            onClick={() => {
                              mutation.reset()
                              setErrors({})
                              setNotice('')
                              setEditing(comment)
                              setContent(comment.content)
                            }}
                          >
                            Sửa
                          </Button>
                        )}
                      </li>
                    ))}
                  </ol>
                )}
                {query.data.totalPages > 1 && (
                  <Stack direction="horizontal" gap={2} className="mb-3">
                    <Button
                      variant="outline-secondary"
                      disabled={
                        page === 0 || query.isFetching || mutation.isPending
                      }
                      onClick={() => setPage(page - 1)}
                    >
                      Bình luận trước
                    </Button>
                    <span>
                      Trang {page + 1}/{query.data.totalPages}
                    </span>
                    <Button
                      variant="outline-secondary"
                      disabled={
                        page + 1 >= query.data.totalPages ||
                        query.isFetching ||
                        mutation.isPending
                      }
                      onClick={() => setPage(page + 1)}
                    >
                      Bình luận sau
                    </Button>
                  </Stack>
                )}
              </>
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
                      resetDraft()
                      await query.refetch()
                    }}
                  >
                    Tải lại bình luận
                  </Button>
                )}
              </Alert>
            )}
            {query.isSuccess &&
              (editing
                ? canEditComment(ticket, user, editing)
                : canCreateComment(ticket, user)) && (
                <Form noValidate onSubmit={submit}>
                  <fieldset disabled={mutation.isPending || conflict}>
                    <Form.Group
                      controlId="ticket-comment-content"
                      className="mb-3"
                    >
                      <Form.Label>
                        {editing
                          ? 'Sửa nội dung bình luận'
                          : 'Nội dung bình luận'}
                      </Form.Label>
                      <Form.Control
                        as="textarea"
                        rows={3}
                        required
                        maxLength={COMMENT_MAX_LENGTH}
                        value={content}
                        isInvalid={Boolean(fieldErrors.content)}
                        aria-invalid={Boolean(fieldErrors.content)}
                        onChange={(event) => {
                          setContent(event.target.value)
                          setErrors({})
                          mutation.reset()
                        }}
                      />
                      <Form.Control.Feedback type="invalid">
                        {fieldErrors.content}
                      </Form.Control.Feedback>
                    </Form.Group>
                    {!editing && (
                      <Form.Group
                        controlId="ticket-comment-visibility"
                        className="mb-3"
                      >
                        <Form.Label>Phạm vi bình luận</Form.Label>
                        <Form.Select
                          value={effectiveVisibility}
                          isInvalid={Boolean(fieldErrors.visibility)}
                          onChange={(event) => {
                            setVisibility(event.target.value)
                            setErrors({})
                            mutation.reset()
                          }}
                        >
                          <option value="PUBLIC">Công khai</option>
                          {canCreateInternal && (
                            <option value="INTERNAL">Nội bộ</option>
                          )}
                        </Form.Select>
                        <Form.Control.Feedback type="invalid">
                          {fieldErrors.visibility}
                        </Form.Control.Feedback>
                      </Form.Group>
                    )}
                    <Button type="submit">
                      {mutation.isPending
                        ? 'Đang lưu…'
                        : editing
                          ? 'Lưu bình luận'
                          : 'Gửi bình luận'}
                    </Button>
                    {editing && (
                      <Button variant="link" onClick={resetDraft}>
                        Hủy sửa
                      </Button>
                    )}
                  </fieldset>
                </Form>
              )}
          </>
        )}
      </Card.Body>
    </Card>
  )
}
