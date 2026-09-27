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
