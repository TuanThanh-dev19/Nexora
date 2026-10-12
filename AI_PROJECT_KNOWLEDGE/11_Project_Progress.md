# Project Progress

> **Decision update (2026-09-25):** product/technical planning blockers have been resolved by `00_Approved_Decisions.md`. The project is ready for roadmap/backlog drafting, but implementation progress below remains unchanged and member/date placeholders must be replaced when factual data is available.

## Status Snapshot

**Current phase:** initialization, database design and application scaffolding.

**Business-feature completion:** no end-to-end business feature is currently implemented.

## Completed / Implemented Foundation

- Repository layout for backend, frontend, docs and SQL Server infrastructure.
- Java/Spring Boot and React/Vite dependency setup.
- Docker Compose SQL Server setup and empty-database initialization.
- Flyway migrations V1-V7 with 29 tables, constraints, indexes and starter reference data.
- Database migration verification test using SQL Server Testcontainers.
- Backend health endpoint, OpenAPI config, CORS/security foundation and common error handling.
- Frontend app shell, router, query client, shared Axios client and reusable starter components.
- UI-only demo login/route guards.
- Static/starter Home, Login, Dashboard and Ticket List pages; placeholder admin pages.
- GitHub Actions quality pipeline and pull-request checklist.
- Architecture, database, project structure and local setup documentation.

Items above are foundation only unless explicitly stated; they do not constitute a completed business workflow.

## In Progress

Repository state does not provide reliable evidence that a business feature is actively in progress. Existing package markers, sample API hooks and placeholder pages express intended structure, not progress completion.

## Designed but Not Implemented

- User/role/technician domain behavior.
- Location/equipment/asset management.
- Dynamic category form editing, publishing and server validation.
- Ticket reporting, review, assignment, status workflow, work logs, comments and attachments.
- SLA calculation and enforcement.
- Feedback.
- Dashboard metrics.
- Optional notification, audit and violation/appeal behavior.

## Not Started / No Implementation Evidence

- Business JPA entities and repositories.
- Business services and transaction policies.
- Business REST controllers and request/response DTOs.
- Real authentication/token issuance/current-user flow.
- Backend role/permission enforcement per use case.
- Attachment storage.
- Connected frontend business data flows.
- Business workflow/authorization/validation tests.
- End-to-end test and deployment configuration.

## Current Readiness

### Ready for clarification and technical planning

- Database entity/relationship review.
- MVP scope confirmation.
- Role/permission and ticket transition workshops.
- API conventions and vertical-slice issue decomposition.
- Team assignment planning after member data is supplied.

### Technically close to starting after minimum decisions

- JPA mapping/repositories for stable reference entities.
- Read-only location, equipment type, category/form and skill/SLA queries.
- Real authentication foundation after token/role decisions.
- Ticket creation slice after validation, numbering and attachment decisions.

### Not ready for accurate implementation issues

- Full workflow actions without transition/permission matrix.
- SLA/dashboard without metric/timing rules.
- Notification/audit/violation/appeal without scope and acceptance criteria.
- Week-by-week member assignment without capacity and deadline data.

## Progress Risks / Known Alignment Items

- Frontend `USER` role conflicts with database `REQUESTER`.
- Frontend status/type constants do not fully match database vocabulary.
- Authentication is demo-only on frontend and validation-only on backend.
- Maven includes MongoDB dependency with no documented use.
- Ticket frontend sample calls a backend endpoint that does not exist.
- Optional/P1 tables in schema may be mistaken for implemented features unless status is kept explicit.

## Missing Planning Information

1. Approved product scope and prioritized user journeys.
2. Detailed role/permission matrix.
3. Exact ticket transition diagram and rules.
4. Authentication, attachment storage and deployment decisions.
5. SLA timing/escalation rules and dashboard metric definitions.
6. UI wireframes/navigation and acceptance expectations.
7. Team names, skills, availability and confirmed ownership.
8. Current week, deadline, milestones, demo date and sprint cadence.
9. Definition of Ready, Definition of Done, review/test requirements and issue-size convention.
10. Decision whether notifications, audit, accuracy review, violation and appeal belong to MVP.

## Recommended Progress Update Format

For future updates, record evidence rather than intent:

```text
Issue/Feature:
Status: Not Started | In Progress | Blocked | Implemented
Evidence: PR/commit/file/test/demo link
Dependencies:
Owner:
Acceptance Criteria passed:
Remaining risks/TBD:
Last updated:
```

Do not move a feature to `Implemented` until its required backend, frontend, validation, authorization and tests are connected according to the project's own feature-completion convention.
