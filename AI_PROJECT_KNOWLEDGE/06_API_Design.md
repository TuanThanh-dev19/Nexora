# API Design

> **Decision update (2026-09-25):** authentication transport, pagination, concurrency, error and minimum API-module decisions are approved in `DEC-029` through `DEC-032` of `00_Approved_Decisions.md`. Existing implementation remains limited to technical endpoints until code is added.

## Existing APIs

### Technical endpoint

| Method | Path | Purpose | Status |
|---|---|---|---|
| `GET` | `/api/v1/public/health` | Trả `status`, service name và current timestamp | Implemented |

Ngoài endpoint trên, Spring Actuator health và Springdoc/Swagger UI được cấu hình tại `/actuator/health`, `/v3/api-docs/**` và `/swagger-ui.html`.

**No business API implemented yet.** Không có controller/DTO/service/entity/repository nghiệp vụ cho user, category, asset hoặc ticket. `frontend/src/features/tickets/api/ticketApi.js` có lời gọi `GET /tickets`, nhưng backend endpoint tương ứng không tồn tại; đây là starter example, không phải existing API.

## Existing API Infrastructure

- Base path mà frontend dùng: `/api/v1`.
- Axios client đọc `VITE_API_BASE_URL`, mặc định `/api/v1`.
- Vite dev proxy chuyển `/api` tới `http://localhost:8080`.
- Global exception handler trả Spring `ProblemDetail` cho not-found, business-rule và validation errors.
- Spring Security dùng stateless session và JWT resource-server validation HS256.
- Public: `/api/v1/public/**`, actuator health và OpenAPI/Swagger paths.
- Mọi request khác yêu cầu authentication ở backend.
- CORS origins lấy từ configuration; methods gồm GET/POST/PUT/PATCH/DELETE/OPTIONS.

## Suggested API Modules

> **All modules in this section are Suggested - not implemented.** Responsibilities are planning boundaries, not approved endpoints or contracts.

### Authentication API

Possible responsibilities:

- authenticate user and issue the selected token/session format;
- return current-user identity and roles;
- support logout/refresh only if the approved authentication strategy requires them.

Dependencies: authentication lifecycle, password policy and token strategy must be confirmed first.

### User and Role Administration API

Possible responsibilities:

- manage users and status;
- assign/remove roles;
- manage technician profiles, skills and service areas.

Dependencies: permission matrix and role-assignment governance.

### Location and Asset API

Possible responsibilities:

- retrieve/manage location hierarchy and equipment types;
- retrieve/manage assets;
- change asset status while recording history.

Dependencies: admin permissions and asset transition rules.

### Category and Dynamic Form API

Possible responsibilities:

- retrieve active categories/forms for ticket reporting;
- create/edit draft form versions;
- validate, publish and archive versions;
- manage dynamic fields/options and form defaults.

Dependencies: publish permissions, immutability rules and validation schema contract.

### Ticket API

Possible responsibilities:

- create and retrieve tickets;
- search/filter/sort/paginate within authorization scope;
- validate dynamic field values and attachments;
- perform explicit review/assignment/workflow actions;
- add work logs/comments/attachments;
- submit feedback after eligibility checks.

Dependencies: transition policy, role matrix, storage, ticket-number generation and SLA calculation.

### Dashboard API

Possible responsibilities:

- role-appropriate workload/status/SLA metrics;
- approved date/filter dimensions.

Dependencies: agreed metric definitions and operational ticket data.

### Optional Notification, Audit and Violation APIs

Possible responsibilities exist in the schema, but repository documentation marks these areas optional/P1. Do not plan endpoints until scope is approved.

## Suggested API Design Principles

These principles are already documented by the repository but not yet realized for business APIs:

- use validated request/response DTOs, not JPA entities;
- controllers handle HTTP and delegate decisions to services;
- backend enforces permissions, dynamic validation and transitions;
- represent business transitions as explicit actions, not a generic “set any status” endpoint;
- return consistent `ProblemDetail` errors;
- cover role, invalid-input and invalid-transition cases with tests.

## API Questions / TBD

1. Exact authentication endpoints and token/session lifecycle.
2. Stable role names: database `REQUESTER` versus frontend starter `USER`.
3. URI/resource naming convention, pagination model and standard response envelopes.
4. Ticket visibility/search rules per role and required filters/sorts.
5. Ticket create/update DTOs, ticket-number format and idempotency behavior.
6. Explicit transition actions, request fields and actor authorization.
7. Dynamic form schema returned to frontend and canonical JSON value format per field type.
8. Attachment upload protocol, storage provider, size/content restrictions and download authorization.
9. SLA representation in responses and timezone/business-calendar rules.
10. Optimistic concurrency contract for records with `version`.
11. API versioning/deprecation expectations.
12. Whether notification/audit/violation APIs belong to MVP.
