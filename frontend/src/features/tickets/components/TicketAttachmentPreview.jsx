import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Modal } from 'react-bootstrap'
import { useAuth } from '../../auth/context/useAuth'
import { ticketApi } from '../api/ticketApi'
import { ticketViewerKey } from '../hooks/ticketQueryOptions'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { collaborationErrorMessage } from '../constants/ticketCollaboration'

export function TicketAttachmentPreview({ ticketId, attachment, onHide }) {
  const { user } = useAuth()
  const query = useQuery({
    queryKey: [
      'tickets',
      ...ticketViewerKey(user),
      'attachment-content',
      String(ticketId),
      String(attachment.id),
    ],
    queryFn: ({ signal }) =>
      ticketApi.attachmentContent(ticketId, attachment.id, signal),
    retry: false,
    gcTime: 0,
  })
  const [preview, setPreview] = useState({ url: '', error: false })
  const { url, error: imageError } = preview
  useEffect(() => {
    if (!query.data) return
    const next = URL.createObjectURL(query.data)
    // The modal owns this URL; closing it also cancels an unfinished query.
    // oxlint-disable-next-line react/set-state-in-effect
    setPreview({ url: next, error: false })
    return () => URL.revokeObjectURL(next)
  }, [query.data])
  const ready = url && !query.isFetching && !query.isError && !imageError
  return (
    <Modal show onHide={onHide} size="lg" aria-labelledby="ticket-image-title">
      <Modal.Header closeButton>
        <Modal.Title id="ticket-image-title" className="text-break">
          {attachment.originalName}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {query.isFetching && <LoadingSpinner label="Đang tải ảnh..." />}
        {!query.isFetching && (query.isError || imageError) && (
          <Alert variant="danger">
            <p>
              {imageError
                ? 'Không đọc được nội dung ảnh.'
                : query.error?.response?.status === 404
                  ? 'Tệp không còn khả dụng.'
                  : collaborationErrorMessage(query.error)}
            </p>
            <Button
              variant="outline-danger"
              onClick={() => {
                void query.refetch()
              }}
            >
              Thử lại ảnh
            </Button>
          </Alert>
        )}
        {ready && (
          <img
            key={url}
            src={url}
            alt={attachment.originalName}
            className="img-fluid d-block mx-auto"
            onError={() =>
              setPreview((current) =>
                current.url === url ? { ...current, error: true } : current,
              )
            }
          />
        )}
      </Modal.Body>
      <Modal.Footer>
        {ready && (
          <Button as="a" href={url} download={attachment.originalName}>
            Tải xuống
          </Button>
        )}
        <Button variant="secondary" onClick={onHide}>
          Đóng
        </Button>
      </Modal.Footer>
    </Modal>
  )
}
