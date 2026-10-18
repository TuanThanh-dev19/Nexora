# Ticket attachment content (#76)

`GET /api/v1/tickets/{ticketId}/attachments/{attachmentId}/content` uses the
current database identity and the same union-of-roles visibility specification
as Ticket Detail, including historical assignments. Metadata is queried by both
parent and attachment ID. Only REPORT_EVIDENCE/WORK_RESULT JPEG/PNG are served.
No public mapping, URL token, upload, COMMENT support or range handling is added.

Responses use actual byte length, safe Unicode inline filenames, private/no-store
and nosniff headers. Uploads are capped at 5 MiB; reads are bounded and buffered
before committing headers so I/O failures can return safe JSON problems. Streams
close before the response is returned. Missing files return the same safe 404 as
other unavailable attachments; storage failures return a generic 500.

The existing authenticated arraybuffer client and modal now work with workflow
mocks disabled. Comments, feedback and workflow availability are unchanged.
The modal cancels unused requests and revokes its object URL on cleanup.

## Verification

- Backend: attachment service/controller tests; existing visibility repository
  tests exercise current/history and repeated assignments.
- Frontend: TicketCollaboration tests cover binary error/retry, download,
  cancellation, StrictMode URL cleanup, and images with mock/production guards off.
- For full-stack acceptance: create a Ticket with JPEG/PNG evidence through the
  real Create Ticket API, preview/download after refresh/login, then try a different
  Requester and mismatched ticket/attachment IDs. Missing storage must affect only
  the image modal, not Ticket Detail.
- Browser/full-stack evidence must be recorded separately; unit/MSW tests do not
  establish live storage persistence or server authorization.
