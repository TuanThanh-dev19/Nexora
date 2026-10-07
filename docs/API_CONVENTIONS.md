# Shared API contracts

Business endpoints use the `/api/v1` base path. Successful non-list responses use their own response DTO and are not wrapped in a generic envelope.

## RFC 9457 errors

Errors use `application/problem+json` and Spring `ProblemDetail`. The `type` URI is a stable machine-readable identifier; clients must not branch on the human-readable `title` or `detail`.

Validation example:

```json
{
  "type": "https://nexora.app/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/v1/tickets",
  "fieldErrors": {
    "title": "must not be blank"
  }
}
```

Shared problem types:

| Condition | Status | Type suffix |
| --- | ---: | --- |
| Invalid fields | 400 | `validation-failed` |
| Unreadable JSON | 400 | `malformed-request` |
| Invalid page or size | 400 | `invalid-pagination` |
| Unknown sort | 400 | `unsupported-sort` |
| Unknown filter | 400 | `unsupported-filter` |
| No authentication | 401 | `unauthenticated` |
| No permission | 403 | `access-denied` |
| Missing resource | 404 | `resource-not-found` |
| Stale entity version | 409 | `stale-write` |
| Business rule rejection | 422 | `business-rule-violation` |

Error responses never include stack traces, SQL messages, JWT parser details, or exception class names.

## Pagination, sorting, and filters

Pages are zero-based. The default page size is `20`; the maximum is `100`. List endpoints return:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Controllers create `Pageable` values with `PageRequestFactory` and publish an explicit map of allowed API sort names to entity properties. A sort uses `sort=field,asc` or `sort=field,desc`; unknown fields and directions are rejected instead of being passed to JPA. Controllers validate enum-like filter parameters with `RequestParameterValidator.requireAllowedFilter` before querying.

## Optimistic concurrency

Every request DTO that updates or deletes an existing entity implements `VersionedRequest` and exposes a required, non-negative `version` field. Services compare that expected version with the persisted entity version. A mismatch raises `StaleWriteException`; JPA optimistic-lock exceptions are mapped to the same HTTP `409` problem without exposing persistence details.

## Ticket read contract (GitHub #35)

`GET /api/v1/tickets` and `GET /api/v1/tickets/{ticketId}` require a valid access
token and an ACTIVE current database user. Roles are reloaded for every request;
JWT role claims do not grant additional visibility. REQUESTER sees own reports,
TECHNICIAN sees current or historical assignments, MANAGER/ADMIN see all. Multiple
roles grant the union. Both content and count queries apply this scope in SQL.
Missing and invisible detail requests return the same `resource-not-found` 404.

List parameters occur at most once; unknown names, blank values, unsupported
values, and duplicate parameters return 400. Defaults: `page=0`, `size=20`,
`sort=createdAt,desc`. Size is 1–100. Supported sort properties are `createdAt`,
`updatedAt`, and `ticketNumber`; direction is asc/desc (case-insensitive), and a
missing direction means ASC. The internal tie-breaker is always `id ASC`.
Supported filters are canonical `status`, canonical `priority`, and a positive
64-bit `locationId` (exact location, no descendants). Filters combine with AND.
Out-of-range pages and valid filters with no matches return 200 and empty content.
The offset `page * size` must fit a signed 32-bit integer, as required by JPA;
unrepresentable offsets return `invalid-pagination` 400 rather than a server error.

Every summary has `id`, `ticketNumber`, `title`, `status`, `priority`,
`category: {id, code, name}`, `location: {id, code, name}`,
`asset: {id, assetCode} | null`, `reporter: {id, fullName}`,
`slaDueAt | null`, `createdAt`, and `updatedAt`. The list uses the shared
`content/page/size/totalElements/totalPages` envelope.

Detail has the same summary properties plus:

- `description` and optimistic-lock `version`;
- `formVersion: {id, versionNumber, formStatus, fields}` from the version stored
  on the Ticket, including archived versions;
- fields with `id`, `fieldKey`, `label`, `fieldType`, `required`, `displayOrder`,
  and `options: [{value, label}]`; inactive options remain available for history;
- `dynamicValues: [{fieldDefinitionId, valueJson}]`, preserving verbatim JSON
  strings (including numeric precision); IMAGE values use linked attachments;
- `statusHistory: [{id, fromStatus, toStatus, transitionAction, reason,
  changedBy: {id, fullName}, changedAt}]`, ordered by time then ID ascending;
- `attachments: [{id, fieldDefinitionId, attachmentType, originalName,
  contentType, sizeBytes, uploadedBy: {id, fullName}, createdAt}]`, ordered by
  time then ID ascending, for REPORT_EVIDENCE and WORK_RESULT only.

Fields order by display order then ID ascending; options order by display order
then ID ascending within each field. Collections are always arrays; nullable
references remain null. Timestamps retain ISO-8601 offsets. No storage key,
filesystem path, binary, comment attachment, password, or download URL is exposed.
These endpoints perform no writes or workflow actions. OpenAPI publishes both
response schemas and the query parameter allowlists.
