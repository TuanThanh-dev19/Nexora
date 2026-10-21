# Create Ticket — NEX-046 / #78

`POST /api/v1/tickets` accepts `multipart/form-data`. It requires a current ACTIVE database user with REQUESTER; JWT role claims alone do not grant submission access. The response is an unwrapped `201` `{id,ticketNumber,status:"SUBMITTED",version:0}` with `Location: /api/v1/tickets/{id}`. List/Detail read the persisted ticket immediately after commit.

## Multipart contract

Supply exactly one `ticket` part with `Content-Type: application/json` and zero to five repeated `files` parts. Let the browser/HTTP client generate the boundary. All DTO fields, including nested dynamic values and attachment mappings, reject unknown properties, duplicate JSON object keys and scalar coercion (e.g. a string ID). Arrays `dynamicValues` and `attachments` are required, even when empty. IMAGE has no scalar value. Each uploaded file must be mapped exactly once by its zero-based index in the repeated `files` parts; optional `fieldKey` must name an active IMAGE field. General evidence omits `fieldKey`.

Location is always required, including when `category.locationRequired=false`. Title and description are stripped on the server; title is nonblank and at most 200 UTF-16 code units. Description is nonblank NVARCHAR(MAX), subject to request size limits. Client-owned fields are only `categoryId`, `formVersionId`, `locationId`, optional `assetId`, `title`, `description`, `dynamicValues` and `attachments`.

Images are JPEG/PNG, 1–5,242,880 bytes each, with matching magic bytes. Maximum request size is 30 MiB. Form/global and field-specific count, MIME and size rules are checked before writing files. The multipart container enforces 5 MiB per file/30 MiB per request; Tomcat can swallow up to 32 MiB after rejecting an upload so the client can receive Problem Details near those limits.

Example from the repository root (replace IDs/field keys with a real published form and use a real local image):

```powershell
curl.exe --request POST "$env:NEXORA_API_URL/api/v1/tickets" `
  --header "Authorization: Bearer $env:NEXORA_TOKEN" `
  --form "ticket=@docs/examples/create-ticket.json;type=application/json" `
  --form "files=@C:/path/evidence.png;type=image/png"
```

The sample contains no credentials. Set `NEXORA_API_URL` and `NEXORA_TOKEN` outside version control. A request without evidence uses `attachments: []` and omits the files parts, provided the form permits it.

## Transaction and snapshots

The service takes the category write lock also used by publishing. It holds reference locks for the submitted form, location, optional asset, configured skill/SLA and reporter until commit. Asset eligibility matches the existing exact-location query: ACTIVE or UNDER_MAINTENANCE, never a sibling/descendant asset. Concurrent reference/lock conflicts return 409.

The ticket snapshots category priority, the published form's default skill and SLA due time. Missing defaults stay null. Configured defaults must remain active. One server instant drives `createdAt`, sequence number year (Asia/Bangkok), calendar-minute SLA and initial SUBMIT history. Sequence allocation is global and may have gaps after rollback. No `MAX(id)+1` or per-year reset is used.

Ticket, non-IMAGE JSON values, REPORT_EVIDENCE metadata and SUBMIT history are persisted in one SQL transaction. Field-linked images store the field definition ID; general evidence stores null. Each stored file is registered immediately with `FileStorageTransactionCleanup`; failed requests clean up their own files after rollback. Committed evidence is retained. Database rollback does not itself roll back the filesystem. Existing Ticket/form references and values survive later form publication.

## Submission validation boundary

NEX-048 (#80) completes [normalization and shared validation](TICKET_SUBMISSION_VALIDATION.md): optional empty answers are omitted, combined IMAGE requirements are capped at five, field byte/MIME limits return 413/415 and shared fixtures verify decimal, regex and date semantics.

No submission validator existed on the starting `develop` revision. `TicketSubmissionValidator` supplies that missing dependency here, reusing the existing admin rule validator where compatible. The existing admin/backend and frontend rule validators are aligned to offset DATETIME and JPEG/PNG limits without rebuilding CRUD/editor pages. Existing published rules are never rewritten.

Submission accepts all nine field types with exact JSON types, active keys/options, unique dynamic keys/selections, required checks, text length/pattern, numeric min/max/step, date/time bounds and image rules. NUMBER 0 and BOOLEAN false are values. DATETIME values and bounds require an ISO offset (`Z` or `+07:00`), compared as instants. IMAGE rules support JPEG/PNG and cannot exceed global limits. Legacy GIF/WebP, local DATETIME bounds, malformed/unknown rules or contradictory asset requirements yield `422 form-configuration-incompatible`. Administrators must publish a compatible configuration; the API does not silently convert old form content. Newly saved/published forms use the same rule contract. Existing immutable incompatible versions must be replaced by an explicitly corrected draft.

## Errors and frontend

All handled errors use `application/problem+json` with type/title/status/detail/instance. Validation includes fieldErrors. Paths include basic DTO properties, `dynamicValues.<fieldKey>`, `attachments[index]` and `attachments`.

| Status | Meaning                                                                                                                                     |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| 400    | Malformed/unknown JSON, invalid DTO/value/mapping, inactive category/location, invalid asset selection, empty or mismatched-signature image |
| 401    | Invalid or inactive identity                                                                                                                |
| 403    | Current user lacks REQUESTER                                                                                                                |
| 404    | Missing referenced record                                                                                                                   |
| 409    | `form-version-changed`, inactive configured defaults or concurrent reference conflict                                                       |
| 413    | File or request too large                                                                                                                   |
| 415    | Unsupported request/JSON-part/image MIME                                                                                                    |
| 422    | `form-configuration-incompatible`                                                                                                           |
| 500    | Storage/persistence failed and transaction rolled back                                                                                      |

The existing CreateTicketPage uses the real multipart client, prevents double submission, requires a location and validates title length. After 201 it invalidates ticket/dashboard queries and navigates to detail using the persisted ID and number. Field errors are shown without clearing input/evidence. 409 requires a form reload; 413/415 retain images for correction. A network timeout does not automatically retry POST: the server might have committed. The UI asks the requester to check the ticket list before submitting again. There is no idempotency key.

## Verification

`TicketCreateApiContract` runs HTTP/SQL/storage assertions against H2 and opt-in SQL Server. Tests cover types and rules, identities, DTO injection, references/assets, legacy rules, evidence mapping/signatures, rollback after a second-file failure or a real SQL constraint failure, retained committed evidence, unique concurrent numbers and publication blocked until submission commits. The H2 suite additionally starts a real HTTP server to verify multipart parsing, real JWT authorization, creation/detail and file/request size limits. SQL Server tests use the migrated schema and sequence, not H2 substitutes.

```powershell
cd backend
.\mvnw.cmd verify
$env:RUN_SQLSERVER_IT = 'true'
.\mvnw.cmd '-Dtest=TicketCreateSqlServerIntegrationTest' test
Remove-Item Env:RUN_SQLSERVER_IT
```

Frontend MSW/component tests cover the multipart boundary/parts/authentication, success, field errors, 409 reload, retained evidence after 413/415, mandatory location and no automatic network-error retry. Attachment content/download authorization remains a separate API; this change persists and returns the existing safe metadata contract only.
