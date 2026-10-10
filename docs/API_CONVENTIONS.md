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
