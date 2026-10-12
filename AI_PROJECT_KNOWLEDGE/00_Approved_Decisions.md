# Nexora Approved Decision Baseline

## 0. Authority, Status and Precedence

- Decision date: **2026-09-25**.
- Decision authority: the project owner delegated the unresolved decisions to the Tech Lead/Codex for closure.
- Status of every `DEC-*` item in this file: **Approved planning baseline**, unless explicitly marked as an assumption or factual input still required.
- This file has precedence over older `TBD`, `Inferred`, `Likely` or `Suggested` statements in `01`-`11` when they cover the same subject.
- A later explicit project-owner decision supersedes this file and must be recorded with a date.
- Existing schema/source status does not change: a decision is an approved requirement/design, not proof of implementation.

## 1. Delivery Model and Planning Assumptions

### DEC-001 — Delivery window

Nexora will use a relative **8-week MVP plan**. Week 1 begins at the first team kickoff after this baseline is accepted; Week 8 is the final hardening/demo week.

- Exact calendar dates and external school deadline remain factual inputs to fill in.
- Until actual availability is supplied, planning uses five placeholder members and **3 effective development days per member per week**, with approximately 20% contingency already excluded from assignment.

### DEC-002 — Planning cadence

- Weekly planning and review cadence.
- Issues should normally be **0.5-2 effective development days**.
- Larger work must be split by vertical behavior or independently verifiable outcome.
- Each member should have no more than two concurrent implementation issues.

### DEC-003 — MVP success scenario

The mandatory end-to-end demo is:

```text
Requester logs in
→ selects category/location/asset as applicable
→ submits a dynamic-form ticket with evidence
→ Manager reviews and assigns
→ Technician accepts/starts, records work and resolves
→ Requester closes and submits feedback
→ Manager views updated dashboard metrics
```

## 2. Approved Scope

### DEC-004 — MVP modules

The following modules are in MVP:

1. Internal email/password authentication and current-user context.
2. User status and role administration sufficient to operate the demo.
3. Technician profile, skill, service-area, availability and capacity configuration.
4. Location, equipment type, asset and asset-status management.
5. Incident category and versioned dynamic-form management, including draft/publish/archive.
6. Ticket creation, authorized list/search/detail and dynamic-value validation.
7. Review, rejection, cancellation, assignment/reassignment, start, work log, resolution, close and reopen.
8. Public/internal ticket comments.
9. Report-evidence and work-result attachments.
10. SLA due-time calculation and visual breach/at-risk indicators.
11. One feedback record per closed ticket.
12. Minimal role-scoped dashboard.
13. OpenAPI documentation, authorization/validation/workflow tests and one complete integration/demo path.

### DEC-005 — Explicitly out of MVP

The following are P1 or later and must not block MVP:

- notification delivery and notification center;
- generic audit-log feature/API/UI;
- ticket accuracy review;
- violation cases, restrictions and appeals;
- automatic or AI duplicate detection;
- automatic technician assignment or optimization;
- refresh tokens, password reset, email verification and external identity providers;
- SLA pausing, business-hours calendars and automatic escalation;
- comment attachments and advanced media preview;
- advanced analytics/export;
- MongoDB.

Database tables/columns for an out-of-scope area may remain unused. They are not evidence of MVP implementation.

## 3. Canonical Actors and Authorization

### DEC-006 — Canonical role vocabulary

The canonical roles are exactly:

- `REQUESTER`
- `TECHNICIAN`
- `MANAGER`
- `ADMIN`

Frontend `USER` must be replaced by `REQUESTER`. A user may hold multiple roles; effective access is the union of roles, but every business action still checks its domain preconditions.

### DEC-007 — Ticket visibility

| Role | Ticket visibility |
|---|---|
| `REQUESTER` | Tickets where `reporter_id` is the current user |
| `TECHNICIAN` | Tickets with a current or historical assignment to the current technician |
| `MANAGER` | All tickets |
| `ADMIN` | All tickets |

Reference catalogs needed to submit or process tickets are readable by authenticated users, filtered to active/eligible data where applicable.

### DEC-008 — Permission matrix

| Capability | Requester | Technician | Manager | Admin |
|---|:---:|:---:|:---:|:---:|
| Create ticket | Own | No | No | No |
| View permitted ticket | Own | Assigned/history | All | All |
| Add public comment | Own ticket | Assigned ticket | Yes | Yes |
| Add internal comment | No | Assigned ticket | Yes | Yes |
| Cancel ticket | Own, before assignment | No | Yes | Yes |
| Review/reject | No | No | Yes | Yes |
| Assign/reassign/unassign | No | No | Yes | Yes |
| Start/work-log/resolve | No | Assigned technician | No | No |
| Close resolved ticket | Own | No | Yes | Yes |
| Reopen resolved ticket | Own | No | Yes | Yes |
| Submit feedback | Own closed ticket | No | No | No |
| Manage technician profile/coverage | No | Read own | Yes | Yes |
| Manage asset instances/status | No | Read | Yes | Yes |
| Manage users/roles/reference catalogs/forms | No | No | No | Yes |

`ADMIN` may perform manager actions, but does not bypass validation, lifecycle or evidence requirements.

## 4. Approved Ticket Workflow

### DEC-009 — Transition matrix

| Action | From | To | Actor | Required input / side effect |
|---|---|---|---|---|
| Submit | New request | `SUBMITTED` | Requester | Valid category/form, location, asset policy, fields and evidence; create initial history |
| Begin review | `SUBMITTED` | `UNDER_REVIEW` | Manager/Admin | No reason required |
| Reject | `SUBMITTED`, `UNDER_REVIEW` | `REJECTED` | Manager/Admin | Non-blank reason required |
| Cancel before assignment | `SUBMITTED`, `UNDER_REVIEW` | `CANCELLED` | Owning Requester, Manager/Admin | Non-blank reason required |
| Assign | `UNDER_REVIEW`, `REOPENED` | `ASSIGNED` | Manager/Admin | Eligible technician or documented override; create active assignment |
| Start work | `ASSIGNED` | `IN_PROGRESS` | Assigned Technician | Set `accepted_at`; only current active assignee |
| Unassign | `ASSIGNED`, `IN_PROGRESS` | `UNDER_REVIEW` | Manager/Admin | Reason required; end active assignment |
| Reassign | `ASSIGNED`, `IN_PROGRESS` | `ASSIGNED` | Manager/Admin | Reason required; end prior assignment and create a new active assignment atomically |
| Cancel after assignment | `ASSIGNED`, `IN_PROGRESS`, `REOPENED` | `CANCELLED` | Manager/Admin | Reason required; end active assignment if present |
| Resolve | `IN_PROGRESS` | `RESOLVED` | Assigned Technician | Resolution outcome and resolution summary required; at least one work log; end active assignment; set `resolved_at` |
| Close | `RESOLVED` | `CLOSED` | Owning Requester | Set `closed_at`; no reason required |
| Force close | `RESOLVED` | `CLOSED` | Manager/Admin | Reason required; set `closed_at` |
| Reopen | `RESOLVED` | `REOPENED` | Owning Requester, Manager/Admin | Reason required; clear `resolved_at`; calculate a new current SLA due time |

`CLOSED`, `REJECTED` and `CANCELLED` are terminal in MVP. No generic “set status” endpoint is allowed.

### DEC-010 — Concurrency and history

- Every successful transition writes `ticket_status_history` in the same transaction as the ticket update.
- `tickets.version` is used for optimistic locking.
- Update/action requests carry the expected `version`; stale changes return HTTP `409 Conflict`.
- Assignment end/create and related status update are atomic.

### DEC-011 — Resolution rules

- Allowed outcomes remain `FIXED`, `TEMPORARY_FIX`, `NO_FAULT_FOUND`, `DUPLICATE`, `UNRESOLVED` at schema level.
- MVP UI exposes `FIXED`, `TEMPORARY_FIX`, `NO_FAULT_FOUND` and `UNRESOLVED`.
- `DUPLICATE` handling is P1 and is not exposed in the MVP UI/API.
- `NO_FAULT_FOUND` requires a clear resolution summary but never creates a violation automatically.

## 5. Ticket Creation and Dynamic Data

### DEC-012 — Ticket-number generation

Ticket number format is:

```text
NX-{four-digit-year}-{six-digit-sequence}
```

Example: `NX-2026-000123`.

A SQL Server sequence introduced by a new Flyway migration is the authoritative concurrency-safe counter. The sequence does not reset annually; the year represents creation year and the numeric part is globally increasing.

### DEC-013 — Duplicate detection

No automatic duplicate detection in MVP. `duplicate_of_ticket_id` and `DUPLICATE` resolution outcome are reserved for P1. The team must not build similarity search or hidden duplicate behavior during MVP.

### DEC-014 — Asset requirement precedence

Both existing fields are used with this deterministic rule:

1. `asset_policy = FORBIDDEN`: ticket must not contain an asset and the form may not set `requires_equipment = 1`.
2. `asset_policy = REQUIRED`: ticket must contain an asset.
3. `asset_policy = OPTIONAL`: `category_form_versions.requires_equipment` decides whether asset is required for that version; otherwise it is optional.

Publishing rejects an inconsistent form configuration.

### DEC-015 — Asset eligibility

- A selected asset must belong to the exact ticket location in MVP.
- Only assets with status `ACTIVE` or `UNDER_MAINTENANCE` may be selected for a new ticket.
- `OUT_OF_SERVICE` and `RETIRED` assets remain visible to Manager/Admin but cannot be attached to a new ticket.

### DEC-016 — Category/form selection

- New tickets use the category's single current `PUBLISHED` form version.
- Inactive category/form/fields/options cannot be selected for new tickets.
- Ticket stores the selected form version permanently for historical rendering.

### DEC-017 — Canonical dynamic-value JSON contract

| Field type | `value_json` representation |
|---|---|
| `TEXT`, `TEXTAREA`, `SELECT` | JSON string |
| `NUMBER` | JSON number |
| `DATE` | JSON string in `YYYY-MM-DD` |
| `DATETIME` | ISO-8601 JSON string with offset |
| `BOOLEAN` | JSON boolean |
| `MULTI_SELECT` | JSON array of unique option-value strings |
| `IMAGE` | No scalar value row required; represented by field-linked attachments |

The backend validates required, type, active options and all supported validation metadata. Unknown validation keys cause draft publish to fail rather than being silently ignored.

## 6. Category Form Lifecycle

### DEC-018 — Form ownership and lifecycle

- Only Admin manages category/form definitions.
- Draft forms are editable; published and archived forms are immutable.
- Creating a changed form clones or creates a new draft with the next server-generated version number.
- Publishing validates the complete form, archives the previously published version and publishes the new version in one transaction.
- Existing tickets continue rendering from their stored form version.

### DEC-019 — Publish validation

Publishing requires:

- active category;
- unique nonblank field keys and deterministic display order;
- recognized validation rules for each field type;
- at least one active option for `SELECT`/`MULTI_SELECT`;
- no options for non-select fields;
- valid asset-policy/form requirement combination;
- active default skill and default SLA policy;
- non-negative attachment minimum.

## 7. Technician Assignment

### DEC-020 — Assignment mode

MVP assignment is **manual with eligibility validation**. The system may show eligible technicians but does not automatically choose one.

### DEC-021 — Eligibility and override

By default a technician must:

- have an active user with `TECHNICIAN` role and a technician profile;
- have `available = 1`;
- be below `max_active_tickets`;
- possess the ticket's required skill when one exists;
- cover the ticket location.

Service-area coverage applies to the configured location and all descendants in the location tree.

Manager/Admin may override availability, capacity, skill or service-area mismatch only with a non-blank `override_reason`. A missing technician profile or missing `TECHNICIAN` role cannot be overridden.

### DEC-022 — Capacity calculation

Capacity equals the number of assignments for that technician where `ended_at IS NULL`. Business code must ensure active assignments only belong to `ASSIGNED` or `IN_PROGRESS` tickets.

Technician acceptance is represented by the `Start work` action: it sets `accepted_at` and moves the ticket to `IN_PROGRESS`.

## 8. SLA

### DEC-023 — Authoritative SLA source

`category_form_versions.default_sla_policy_id` is authoritative for new tickets. `incident_categories.sla_hours` is legacy compatibility data and must not drive new runtime calculations.

A published form must have an active SLA policy. Existing policies already referenced by a published form must not be edited destructively; create a new policy/form version.

### DEC-024 — SLA calculation

- SLA starts at ticket creation.
- MVP uses calendar minutes, not business hours.
- `sla_due_at = created_at + resolution_minutes` from the published form's policy.
- The calculated due timestamp is the ticket's snapshot; later policy changes do not recalculate existing tickets.
- SLA stops being active in `RESOLVED`, `CLOSED`, `REJECTED` or `CANCELLED`.
- Reopen calculates a new `sla_due_at` from reopen time using the ticket form version's policy.

### DEC-025 — SLA pause, breach and at-risk

- No SLA pause in MVP because there is no approved waiting-for-requester state. `pause_waiting_requester` is reserved for P1.
- Breached: active ticket and current time is after `sla_due_at`.
- At risk: active ticket and `sla_due_at` is within the next 2 hours.
- MVP displays breached/at-risk status only; automatic notification/escalation is P1.

## 9. Feedback, Comments and Attachments

### DEC-026 — Feedback

- Only the ticket reporter may create feedback.
- Ticket must be `CLOSED`.
- Exactly one feedback per ticket.
- Rating 1-5; comment optional.
- Feedback is immutable in MVP; no update/delete endpoint.

### DEC-027 — Comments

- `PUBLIC`: visible to the reporter, current/historical assigned technicians, Manager and Admin.
- `INTERNAL`: visible only to Technician, Manager and Admin.
- Requester may only create public comments on own nonterminal ticket.
- Technician may comment only on a currently assigned ticket; Manager/Admin may comment on any nonterminal ticket.
- Author may edit their own comment while ticket is nonterminal; no comment deletion in MVP.

### DEC-028 — Attachment storage and limits

- MVP uses configurable local filesystem storage outside the public web root, backed by a persistent Docker/deployment volume.
- Database stores only metadata and an opaque generated storage key.
- Files are uploaded/downloaded only through authorized API endpoints.
- Allowed types: `image/jpeg`, `image/png`.
- Maximum size: 5 MiB per file; maximum 5 files per upload request.
- Original filename is display metadata only and never used as a filesystem path.
- MVP supports `REPORT_EVIDENCE`, form-linked `IMAGE` evidence and `WORK_RESULT`.
- `COMMENT` attachments are P1 because the current schema has no direct comment FK.

## 10. Authentication and API Contract

### DEC-029 — Authentication strategy

- Internal email/password login.
- Passwords hashed with BCrypt; never logged or returned.
- Stateless HS256 bearer access JWT using the existing backend foundation.
- JWT lifetime: 8 hours.
- No refresh token in MVP; expired token requires login again.
- JWT `sub` is the user ID and includes role codes as a claim.
- Every secured business action also verifies that the database user is still `ACTIVE`.
- Frontend stores the access token in `sessionStorage` for the current browser session and sends `Authorization: Bearer <token>`.
- Axios `withCredentials` is disabled for the MVP bearer-token strategy.

### DEC-030 — Bootstrap and user provisioning

- Admin creates/disables users and assigns roles through protected admin functions.
- Initial local/demo Admin is created once from environment-provided bootstrap credentials when no Admin exists.
- Bootstrap is disabled by default outside the local/demo profile; no real password is committed.

### DEC-031 — API conventions

- Base path: `/api/v1`.
- JSON request/response DTOs; never expose JPA entities.
- Errors use RFC 9457/Spring `ProblemDetail` with stable problem type and field errors where relevant.
- Pagination is zero-based: default size 20, maximum 100.
- List responses use `{content, page, size, totalElements, totalPages}`.
- Sort fields are endpoint allowlists; unknown filters/sorts return `400`.
- Expected entity `version` is included in mutating request DTOs; stale version returns `409`.
- Business transitions use explicit action endpoints, not a generic status patch.

### DEC-032 — Minimum business API surface

The MVP API modules are approved as:

- Auth: login and current user.
- Admin users/roles and technician configuration.
- Locations, equipment types, assets and asset-status actions.
- Categories, form versions, fields/options and publish/archive actions.
- Tickets: create, authorized search/list, detail.
- Explicit ticket actions: review, reject, cancel, assign/reassign/unassign, start, resolve, close, reopen.
- Ticket subresources: work logs, comments, attachments and feedback.
- Role-scoped dashboard summary.

Exact DTO field lists belong in individual issue contracts, but may not contradict this decision baseline.

## 11. UI Decisions

### DEC-033 — MVP page inventory

MVP includes:

- Login.
- Role-aware dashboard.
- Ticket list with URL-based search/filter/sort/page.
- Ticket creation using the current published dynamic form.
- Ticket detail with status/history, dynamic values, attachments, comments and role/state actions.
- My assigned work view for Technician.
- Asset/location management for Manager/Admin according to permissions.
- Category/form version editor and publish flow for Admin.
- User/role and technician configuration for Admin/Manager according to the matrix.
- Unauthorized/not-found/error states.

Notifications, audit, violation/appeal and advanced reporting pages are not MVP.

### DEC-034 — UI quality baseline

- Responsive for desktop and common tablet widths; mobile must remain usable for ticket submission and technician actions.
- Every data page has loading, empty, error and success feedback states.
- Form controls have labels, keyboard access and validation messages.
- Backend error messages are mapped safely; authorization is never inferred solely from hidden buttons.
- Vietnamese is the primary demo content language; source identifiers and API enums remain English.

## 12. Dashboard

### DEC-035 — MVP metrics

The dashboard contains exactly these initial metrics:

1. Open tickets: statuses not terminal and not `RESOLVED`.
2. In progress: status `IN_PROGRESS`.
3. SLA at risk: active tickets due within the next 2 hours.
4. Resolved this week: `resolved_at` within the current ISO week in Asia/Bangkok display context.

Scope is role-based: requester sees own tickets, technician sees current/historical assigned tickets, Manager/Admin sees all tickets. Advanced charts and exports are P1.

## 13. Team Ownership and Review

### DEC-036 — Placeholder vertical-slice ownership

Until real names/skills are supplied:

| Placeholder | Primary ownership |
|---|---|
| Member 1 | Authentication, user, roles, security integration |
| Member 2 | Ticket creation and runtime dynamic-form rendering/validation |
| Member 3 | Ticket workflow, assignment, work logs and resolution |
| Member 4 | Location, equipment/assets and category-form administration |
| Member 5 | Ticket search/detail, dashboard, integration/E2E and CI quality |

Shared contracts are reviewed before parallel frontend/backend work begins. Ownership is end-to-end, including backend, frontend and tests.

### DEC-037 — Review ownership

- No author self-approves a pull request.
- Member 1 is default security/auth reviewer.
- Member 4 is default schema/migration reviewer.
- Member 5 is default test/integration reviewer.
- At least one cross-owner review is required; security or migration changes also require the relevant specialist reviewer.

Actual names may replace placeholders without changing the module plan.

## 14. Definition of Ready and Done

### DEC-038 — Definition of Ready

An implementation issue is Ready only when it has:

- user/business outcome and actor;
- status: confirmed requirement or explicit decision reference;
- bounded scope and out-of-scope notes;
- dependencies and owner;
- API/UI/schema impact identified;
- testable acceptance criteria, including permission and invalid-input cases;
- no unresolved P0 domain question.

### DEC-039 — Definition of Done

An issue is Done only when applicable items are complete:

- backend/frontend/database behavior is connected end-to-end;
- authorization and server-side validation are implemented;
- migrations are additive and verified against SQL Server when schema changes;
- automated happy-path, invalid-input and permission/state tests pass;
- loading/empty/error/success UI states exist;
- OpenAPI and Project Knowledge are updated when contracts change;
- no secret is committed;
- CI passes and required review is approved;
- acceptance criteria are demonstrated.

## 15. Approved Relative Roadmap

### Week 1 — Foundation contracts

- align role constants and dynamic/status enums;
- JPA mappings/repositories for core identity/reference entities;
- authentication backend/frontend skeleton;
- shared API/error/pagination/concurrency contracts;
- test data/bootstrap strategy.

### Week 2 — Authentication and reference data

- real login/current-user and route/API authorization;
- user/role/technician configuration foundation;
- read APIs/UI for location, category published forms, skills/SLA and eligible assets;
- SQL Server integration fixtures.

### Week 3 — Configuration/admin slices

- location/equipment/asset management and asset status history;
- category draft/version/field/options editor and publish validation;
- technician skills/service areas/capacity configuration.

### Week 4 — Ticket creation

- ticket-number sequence;
- create-ticket backend transaction and dynamic validation;
- evidence upload;
- dynamic ticket form UI;
- creation integration tests.

### Week 5 — Ticket query and collaboration

- authorized list/search/filter/sort/pagination;
- ticket detail/history/dynamic rendering;
- public/internal comments;
- role-aware frontend actions shell.

### Week 6 — Workflow and execution

- review/reject/cancel;
- assignment eligibility/override/reassign/unassign;
- technician start/work logs/work-result upload/resolve;
- workflow and concurrency tests.

### Week 7 — Completion and reporting

- close/reopen and feedback;
- SLA indicators;
- four dashboard metrics;
- full happy-path integration and authorization matrix testing.

### Week 8 — Hardening and demo

- bug fixing and UX/accessibility pass;
- SQL Server clean-environment verification;
- security/validation regression;
- OpenAPI/readme/demo data and scripted demo rehearsal;
- release candidate freeze.

## 16. Remaining Factual Inputs — Not Product Decisions

The following cannot be invented and must be supplied when available:

1. Exact final deadline/demo date and any lecturer checkpoints.
2. Actual current project week.
3. Names of the five members.
4. Actual weekly availability, unavailable dates and skill profiles.
5. Deployment host/account available to the team.

Until supplied, roadmap/issues may use relative weeks and Member 1-5 placeholders.

## 17. Readiness Decision

### Roadmap and backlog

**READY.** This baseline resolves the product and technical blockers needed to draft the dependency roadmap, epics and implementation issues.

### Acceptance Criteria

**READY for MVP issues**, provided each issue cites the relevant `DEC-*` decisions and stays within the approved scope.

### Weekly assignment

**READY provisionally** using Week 1-8 and Member 1-5 placeholders. Replace placeholders and capacity assumptions when real team data/calendar dates are provided.

### GitHub issue creation

No GitHub issue is created by this decision record. Issue creation requires a separate explicit request.

