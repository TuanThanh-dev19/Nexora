# Project Roadmap

> **Decision update (2026-09-25):** the relative Week 1-8 roadmap in `DEC-001` and section 15, "Approved Relative Roadmap", of `00_Approved_Decisions.md` is the approved planning baseline. Exact calendar dates remain to be supplied.

## Current Phase

**Project initialization / database design / technical and UI scaffolding.**

Repository đã có architecture conventions, SQL Server schema V1-V7, seed data, configuration, CI và basic UI shell. Business entities/services/APIs và end-to-end integration chưa bắt đầu hoặc chưa tồn tại trong source.

## Confirmed Milestones

Không có milestone calendar, sprint dates hoặc approved release plan trong repository.

Các delivery facts được tài liệu xác nhận:

- intended scope là một modular-monolith MVP trong 8 tuần;
- demo checklist cần một end-to-end flow: create ticket → review → assign → process → resolve → feedback;
- notification, audit và reviewed false-report areas là optional; violation/appeal được ghi là P1.

Mốc thời gian cụ thể và Definition of Done: **TBD**.

## Suggested Technical Implementation Order

> **This section is a technical planning suggestion, not an approved project requirement.** Thứ tự dựa trên dependency hiện có; cần điều chỉnh sau khi product requirements và team capacity được xác nhận.

### 1. Planning and Contract Alignment

- confirm MVP scope, role/permission matrix and ticket transition matrix;
- resolve `REQUESTER` versus frontend `USER` vocabulary;
- decide authentication/token and attachment-storage strategies;
- define API conventions, Definition of Done and minimal acceptance-test scenarios.

Why first: các quyết định này ảnh hưởng gần như mọi business issue và tránh implement lại.

### 2. Backend Domain Foundation

- map core JPA entities and repositories to existing Flyway schema;
- establish DTO/mapping conventions, optimistic locking and integration-test fixtures;
- implement read-only reference access for roles/categories/locations/assets as required by first workflow.

Dependencies: approved field semantics; schema must remain source-controlled by new migrations only.

### 3. Real Authentication and Authorization

- credential verification/current user/token lifecycle according to approved strategy;
- database-role to Spring authority mapping;
- replace frontend demo auth and validate backend permission enforcement.

Dependencies: role matrix and security decision.

### 4. Reference and Configuration Slices

- location/equipment/asset access needed by ticket creation;
- category published-form retrieval and server-side dynamic validation;
- technician skill/service-area/reference access needed by assignment.

Dependencies: domain foundation and authorization.

### 5. Ticket Creation Vertical Slice

- create ticket with category/form, location, optional asset, dynamic values and required evidence;
- generate ticket number and initial history according to confirmed rules;
- implement frontend create experience and tests.

Dependencies: auth, category/form, location, asset, validation and storage decisions.

### 6. Ticket Query and Detail Vertical Slice

- authorized list/search/filter/sort/pagination;
- ticket detail including dynamic values, history and attachments;
- connect existing ticket list starter to real APIs and URL parameters.

Dependencies: auth and persisted ticket data/API conventions.

### 7. Review, Assignment and Work Execution

- explicit review/status actions;
- technician selection/assignment/accept/end rules;
- work logs, comments and work-result attachments;
- frontend actions constrained by role/status.

Dependencies: transition matrix, technician model, ticket query/detail and storage.

### 8. Resolution, Closure and Feedback

- resolution outcomes and timestamps;
- close/reopen/cancel/reject paths only as approved;
- reporter feedback and end-to-end happy/error paths.

Dependencies: workflow policy and ticket execution.

### 9. Dashboard and Quality Hardening

- approved metrics and SLA calculation;
- authorization, invalid transition, concurrency and validation tests;
- integration/E2E demo flow, performance/accessibility/security checks;
- update Swagger and operational documentation.

Dependencies: stable business workflows and representative data.

### 10. Optional/P1 Scope

- notifications, audit feature surfaces, accuracy review, violation and appeal only after MVP confirmation.

Dependencies: approved scope and completed core flow.

## High-Level Flow

```text
Requirements / role and workflow decisions
                    ↓
Domain persistence + API conventions
                    ↓
Authentication / authorization
                    ↓
Reference data + category form retrieval/validation
                    ↓
Ticket creation → query/detail → review/assignment → work → resolve/close → feedback
                    ↓
Dashboard, hardening, integration test and deployment readiness
                    ↓
Optional/P1 modules
```

## Parallelizable Work

After shared contracts are stable, likely parallel work includes:

- Location/equipment/asset slice and category/form read slice.
- Backend reference APIs and frontend reference selectors, using agreed contracts/mocks.
- Authentication backend and non-auth visual/layout work, provided protected API integration waits for auth contract.
- Ticket query UI and ticket create UI after response/request contracts are fixed.
- Test fixtures/CI hardening alongside feature slices.
- Dashboard UI skeleton only after metric contract is agreed; real queries depend on workflow data.

**Likely dependency, requires team confirmation:** parallel work should be vertical-slice oriented and avoid simultaneous edits to router/security/shared contracts without an owner.

## Critical Dependencies

| Dependency | Blocks |
|---|---|
| MVP scope and actor permissions | Issue acceptance criteria, protected APIs/UI |
| Ticket transition matrix | Review, assignment, work, resolution, close/reopen/cancel |
| Authentication strategy | All protected business integration |
| Role vocabulary alignment | Backend/frontend authorization tests |
| Dynamic-form value contract | Ticket creation/detail |
| Attachment storage/security | Evidence and work-result completion |
| SLA rules | Due dates, breach logic, dashboard |
| Ticket-number/idempotency rules | Reliable create-ticket API |
| Team availability/skills | Week-by-week assignment and estimates |

## Roadmap Information Still Needed

- Project start/current week, deadline and demo/review dates.
- Approved MVP/P1/out-of-scope list.
- Product owner decisions for all TBD requirements.
- Team member profiles and capacity.
- Expected issue sizing/sprint cadence.
- Deployment target and release gates.
