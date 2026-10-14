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

## Manager ticket workflow UI/mock contract (#52)

This section fixes the frontend/demo contract for the later backend milestone.
**The workflow endpoints and detail assignment extension are not implemented in
the backend by this issue.** Existing real ticket reads retain the contract above.
Production keeps workflow controls unavailable and never substitutes fixture data.

### Actions and versions

Only MANAGER/ADMIN controls are implemented here, using the existing
`TicketWorkflowContract` and approved DEC-009/DEC-021 rules. No generic status patch
or client-supplied target status is accepted.

| POST suffix under `/api/v1/tickets/{ticketId}` | JSON body | Allowed source → target |
| --- | --- | --- |
| `/review` | `{version}` | SUBMITTED → UNDER_REVIEW |
| `/reject` | `{version,reason}` | SUBMITTED/UNDER_REVIEW → REJECTED |
| `/cancel` | `{version,reason}` | SUBMITTED/UNDER_REVIEW/ASSIGNED/IN_PROGRESS/REOPENED → CANCELLED |
| `/assign` | `{version,technicianId,overrideReason?}` | UNDER_REVIEW/REOPENED → ASSIGNED |
| `/reassign` | `{version,technicianId,reason,overrideReason?}` | ASSIGNED/IN_PROGRESS → ASSIGNED |
| `/unassign` | `{version,reason}` | ASSIGNED/IN_PROGRESS → UNDER_REVIEW |

`version` is the required non-negative **ticket** version, including zero, captured
when the confirmation dialog opens. It is never the technician profile version.
`technicianId` is the user ID shared with `technician_profiles.technician_id`.
Reasons are non-blank and limited to 500 characters for this UI/mock contract, so
they fit both history and assignment end reasons. Override reasons are non-blank
when needed and limited to 1,000 characters. Input is trimmed, never truncated.
Later backend owners must implement/confirm these documented request limits.

The common mock action result is `{id,status,version,currentAssignment}`. Each
success increments version, updates `updatedAt`, appends canonical status history,
and changes assignment records together. `/cancel` writes
`CANCEL_BEFORE_ASSIGNMENT` from SUBMITTED/UNDER_REVIEW and
`CANCEL_AFTER_ASSIGNMENT` otherwise. Reassignment may have identical history source
and target statuses; it still ends the old assignment and creates a new one with
`acceptedAt: null`. Cancellation ends an active assignment when present.

### Current assignment: future additive detail field

The mock detail includes `currentAssignment: null | Assignment`. Real detail today
omits this field; the UI displays "assignment data unavailable" for omission and
"unassigned" only for explicit null. Assignee identity is not inferred from history.

```json
{
  "id": 171,
  "ticketId": 11,
  "technician": {"technicianId": 21, "fullName": "Nguyễn An"},
  "assignedBy": {"id": 5, "fullName": "Quản lý demo"},
  "assignedAt": "2026-10-09T08:00:00+07:00",
  "acceptedAt": null,
  "endedAt": null,
  "endReason": null,
  "overrideReason": null
}
```

### Candidate query and eligibility DTO

`GET /api/v1/tickets/{ticketId}/eligible-technicians` uses only `page`, `size`,
`query`, and `eligibility`. Pagination uses the shared zero-based envelope and
size 1–100 (default 20). `query` is an optional non-blank, case-insensitive substring
of full name/email. Optional `eligibility` is `ELIGIBLE`, `OVERRIDE_REQUIRED`, or
`BLOCKED`; omission means all candidates. Blank, duplicate, or unknown parameters
are rejected with 400. The dialog resets page/selection when search or filters
change. Candidates include overrideable mismatches; the endpoint name does not
mean all returned rows can be assigned without an override.

Each `content` item has:

```json
{
  "technicianId": 24,
  "fullName": "Phạm Dũng",
  "email": "technician24@example.test",
  "userStatus": "ACTIVE",
  "hasTechnicianRole": true,
  "hasProfile": true,
  "available": true,
  "activeTicketCount": 1,
  "maxActiveTickets": 1,
  "skills": [{"id": 1, "name": "Thiết bị trình chiếu"}],
  "serviceAreas": [{"id": 2, "name": "Phòng 201"}],
  "eligibilityReasons": [{"code": "CAPACITY_REACHED", "overridable": true}]
}
```

Profile-less rows have nullable capacity and empty skill/service-area arrays.
Hard-blocked rows are shown disabled for explanation. `UNAVAILABLE`,
`CAPACITY_REACHED`, `SKILL_MISMATCH`, and `COVERAGE_MISMATCH` require a documented
override. `USER_INACTIVE`, `MISSING_TECHNICIAN_ROLE`,
`MISSING_TECHNICIAN_PROFILE`, unknown reason codes, or `overridable: false` cannot
be bypassed. The identity flags also block selection even if reason metadata is
incorrectly permissive.

Eligibility is supplied by this ticket-specific query. Technician profile/list
APIs serve administration and do not replace it. The UI does not calculate skill
matches, location ancestry, or capacity. Mock workload counts derive from fixture
assignments with `endedAt: null`; skill/coverage matches are fixture scenarios,
not a backend eligibility engine.

### Errors and refresh

Mocks use the existing RFC 9457 problem shape: 400 for invalid fields/query,
401 for unavailable authentication, 403 for permission, 404 for unavailable
tickets, 409 `stale-write` for expected-version conflicts, and 422
`business-rule-violation` for invalid transitions/hard eligibility blockers or a
mismatch without the required override. Field problems can include `fieldErrors`.
Clients branch on status/type, never translated error text.

After success the frontend invalidates the viewer's ticket list/detail/candidate
queries and refetches active reads. It does not synthesize missing history from
the action result. A 422 refreshes candidates so changed capacity/eligibility is
visible. A 409 locks submission and offers explicit reload, closing/resetting the
dialog before a new confirmation. Mutations are never retried automatically.
Candidate/action failures stay local; read denial still uses the existing safe
detail error behavior and session/cache cleanup.

Mock tests verify presentation and fixture consistency only, not backend
authorization, SQL transactions, or concurrency guarantees. Backend integration
must confirm these proposed DTOs/query fields before enabling real workflow.

## Technician work UI/mock contract (#53, proposed)

These endpoints are implemented by the development/test mock only. Backend owners
must confirm this contract before the UI can call real write endpoints. Production
continues to use real ticket reads and reports technician work as unavailable.

- `GET /technicians/me/assigned-tickets`: `page`, `size`, `sort`, `status`,
  `priority`, `locationId`. Pagination and filter validation follow the ticket
  read contract. Sorts: `createdAt`, `updatedAt`, `ticketNumber`, each asc/desc;
  default `createdAt,desc`, ties by `id ASC`. Status is limited to `ASSIGNED` and
  `IN_PROGRESS`. Each summary has the existing ticket summary fields plus the
  documented `currentAssignment` object. Only `endedAt: null` assignments for
  the authenticated technician are returned, including the count.
- Detail uses the existing proposed additive `currentAssignment` field. Omission
  means unknown, `null` means no active assignment. Ticket visibility alone never
  authorizes a write.
- `POST /tickets/{id}/start` takes `{version}` and returns
  `{id,status,version,currentAssignment}`. Only the active assignee can move
  `ASSIGNED` to `IN_PROGRESS`; the assignment gains `acceptedAt`.
- `GET /tickets/{id}/work-logs` returns an array ordered by `createdAt ASC, id
  ASC`. A visible ticket can be read, including by a historical assignee. Items
  are `{id,ticketId,technician:{id,fullName},logType,content,startedAt,endedAt,
  minutesSpent,createdAt}`.
- `POST /tickets/{id}/work-logs` takes `{version,logType,content,startedAt,
  endedAt,minutesSpent}`. The active assignee can add a log only in `IN_PROGRESS`.
  Allowed types: `DIAGNOSIS`, `PROGRESS`, `REPAIR`, `RESOLUTION`,
  `NO_FAULT_FOUND`. Content is nonblank. `startedAt` is a valid offset-bearing
  timestamp; `endedAt` and `minutesSpent` may be null. End must not precede
  start; supplied minutes must be a positive integer. The response is 201 with
  the log DTO plus `ticketVersion`, which increments on success in this mock.
- `POST /tickets/{id}/attachments` is multipart with a `metadata` JSON string
  `{attachmentType:"WORK_RESULT"}` and up to five `files` parts. Only the active
  assignee in `IN_PROGRESS` can upload JPEG/PNG images of at most 5 MiB each.
  The mock accepts or rejects the whole request and returns an array of metadata
  matching ticket detail; it does not publish file URLs. Upload does not change
  the ticket version in the mock.
- `POST /tickets/{id}/resolve` takes `{version,resolutionOutcome,
  resolutionSummary}` and returns `{id,status,version,currentAssignment}`.
  Only the active assignee in `IN_PROGRESS` with at least one saved work log can
  resolve. The summary is nonblank. MVP outcomes are `FIXED`, `TEMPORARY_FIX`,
  `NO_FAULT_FOUND`, `UNRESOLVED`. Resolve ends the assignment and records history.

Writes return RFC 9457 problems: 400 malformed/invalid input, 401 unauthenticated,
403 non-Technician actor, 404 invisible ticket, 409 stale ticket version, and 422
invalid state, lost active assignment, or missing resolve prerequisites. A conflict
requires reload and fresh confirmation. Mock mutations are never persisted on the
server. The backend must independently enforce authorization and all rules.

For the browser demo, the first two authenticated Technician IDs encountered are
bound to the two fixture technicians when their IDs differ. This is only an
in-memory fixture adjustment; `/auth/me` still provides the real actor.

## Ticket Detail collaboration UI/mock (#60)

This extends the existing UI/mock contracts. Attachment content, close/reopen,
comments and feedback endpoints remain future backend work. Real list/detail reads
keep their current DTO. `VITE_TICKET_WORKFLOW_MOCK=true` enables development/test
scenarios only; production displays unavailable sections without simulated writes.

### Close, reopen and Requester cancellation

`POST /tickets/{id}/close` accepts `{version,reason?}` from RESOLVED and returns the
existing action result. An owning REQUESTER closes normally (`CLOSE`), including
when they also have Manager/Admin roles. Other Manager/Admin actors force-close
(`FORCE_CLOSE`) with a nonblank reason. This owner-first rule resolves the shared
endpoint for mixed roles and presents one Close control. Later backend integration
must confirm and enforce this contract.

`POST /tickets/{id}/reopen` accepts `{version,reason}` from RESOLVED for an owning
Requester or Manager/Admin. It records REOPEN, clears resolved time and recalculates
SLA from the stored form policy. CLOSED, REJECTED and CANCELLED stay terminal.
Requester cancellation uses the existing `/cancel` endpoint, only for owned
SUBMITTED/UNDER_REVIEW tickets. Manager/Admin cancellation states remain unchanged.
Reasons use the existing 500-character limit; version zero remains valid.

### Attachment content

`GET /tickets/{id}/attachments/{attachmentId}/content` returns JPEG/PNG bytes with
Content-Type, Content-Length and Content-Disposition. It uses ticket visibility,
including historical Technician visibility. Missing content and inaccessible
tickets return safe 404; authorization errors may return 403.

The Axios Authorization client requests ArrayBuffer and constructs a Blob. This
preserves binary bytes because the installed MSW XHR interceptor converts blob
responses through text. The preview modal owns its object URL and releases it on
close/unmount or blob replacement; closing cancels unfinished reads. Download
uses that same blob URL and the metadata filename, never a JWT query parameter.
Uploaded WORK_RESULT bytes remain in the mock store for subsequent content reads.

### Comments

`GET /tickets/{id}/comments?page=0&size=20` uses the shared pagination envelope,
zero-based page and size 1–100 (default 20). Only page/size are accepted. Results
sort by createdAt ascending, then opaque comment ID ascending. Visibility is
applied before pagination and totals. The DTO is:

```json
{
  "id": "opaque-comment-id",
  "author": { "id": 4, "fullName": "Người báo" },
  "content": "Nội dung bình luận",
  "visibility": "PUBLIC",
  "createdAt": "2026-10-09T08:00:00Z",
  "editedAt": null
}
```

`POST /tickets/{id}/comments` accepts `{content,visibility}` and returns 201 DTO.
`PUT /tickets/{id}/comments/{commentId}` accepts `{content}` and returns 200 DTO;
visibility cannot be changed during editing. Content is trimmed and has 1–2000
characters, matching existing persistence validation (`content` maps to `body`).
This provisional contract has no comment version field and never uses ticket
version for comment editing. Comment writes do not increment ticket version.

DEC-006/027 apply: PUBLIC is readable within ticket visibility; INTERNAL requires
Technician/Manager/Admin capability. Requester-only responses contain no INTERNAL
records or counts. Nonterminal creation requires ownership for Requester or current
assignment for Technician; Manager/Admin may create on any nonterminal visible
ticket. Authors may edit their own visible comments while nonterminal, including
historical Technicians. No delete endpoint or comment attachment is included.

### Feedback

`GET /tickets/{id}/feedback` returns 200 DTO or JSON null when absent. Failure is
never treated as absence. `POST /tickets/{id}/feedback` accepts `{rating,comment?}`
and returns 201 `{id,rating,comment,createdAt}`. Rating is an integer 1–5; optional
comment is trimmed and limited to 1000 characters, matching the SQL schema.
Only the owning reporter with REQUESTER capability may submit on CLOSED; one
record per ticket, immutable. A duplicate submission returns 409 `feedback-exists`
and the UI reloads the existing record. No feedback update/delete API is included.

All subresource reads apply ticket visibility first. Errors follow the shared
problem contract: validation 400, session 401, permissions 403, safe missing 404,
conflict 409 and business rules 422. Each section owns its loading/error/retry state;
401 still invokes shared session cleanup. Query caches include viewer ID/roles.
Mock tests demonstrate presentation/contracts, not backend security or persistence.
