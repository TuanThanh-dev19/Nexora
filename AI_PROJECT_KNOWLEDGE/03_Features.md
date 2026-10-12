# Features

> **Decision update (2026-09-25):** MVP inclusion/exclusion và feature ownership đã được chốt trong `00_Approved_Decisions.md` (`DEC-004` đến `DEC-005`, `DEC-036`). Status implementation vẫn không thay đổi cho đến khi source thực sự được phát triển.

> A domain identified from the database is not evidence that its business feature is implemented. Không module nào dưới đây được đánh dấu `Implemented` nếu chưa có flow backend + frontend hoàn chỉnh.

## Feature Inventory

### Authentication and User Management

- Purpose: quản lý user, role, đăng nhập và current-user context.
- Related entities: `app_users`, `roles`, `user_roles`.
- Expected actors: `REQUESTER`, `TECHNICIAN`, `MANAGER`, `ADMIN`; quyền cụ thể TBD.
- Dependencies: security/token strategy; role vocabulary alignment.
- Status: **Designed**. Database và JWT validation scaffold tồn tại; frontend chỉ có demo session; chưa có auth API.

### Technician Workforce Management

- Purpose: quản lý technician profile, availability, capacity, skills và service areas.
- Related entities: `technician_profiles`, `skills`, `technician_skills`, `technician_service_areas`.
- Expected actors: Technician, Manager/Admin; permission details TBD.
- Dependencies: User/Role, Location, Skill reference data.
- Status: **Designed** from database; no service/API/UI.

### Location Management

- Purpose: quản lý cây campus/building/floor/room/area và các location đang active.
- Related entities: `locations`.
- Expected actors: Admin/Manager is likely but not confirmed.
- Dependencies: self-referencing location hierarchy.
- Status: **Designed** from database; no business implementation.

### Equipment and Asset Management

- Purpose: quản lý equipment type, asset, current status và status history.
- Related entities: `equipment_types`, `assets`, `asset_status_history`.
- Expected actors: Manager/Admin is suggested by current frontend route guard; exact matrix TBD.
- Dependencies: Location, User; optionally Ticket for status-history provenance.
- Status: **Designed**. Database exists; frontend only has badge/placeholder.

### Incident Category and Dynamic Form Management

- Purpose: quản lý category, versioned forms, field definitions/options, validation metadata, default skill và SLA.
- Related entities: `incident_categories`, `category_form_versions`, `field_definitions`, `field_options`.
- Expected actors: documentation says administrator creates/edits/publishes; exact role matrix requires confirmation.
- Dependencies: User, Skill, SLA Policy.
- Status: **Designed**. Database and preview component exist; publishing/validation flow not implemented.

### Ticket Reporting

- Purpose: tạo ticket với reporter, category/form version, location, optional asset, description và dynamic values/evidence.
- Related entities: `tickets`, `ticket_field_values`, `ticket_attachments`.
- Expected actors: Requester; creation by other roles TBD.
- Dependencies: User, Category/Form, Location, optional Asset, storage strategy.
- Status: **Designed**. Database exists; ticket list UI is starter only and is not connected.

### Ticket Review and Workflow

- Purpose: review ticket và điều khiển status lifecycle bằng policy code được test.
- Related entities: `tickets`, `ticket_status_history`, `ticket_accuracy_reviews`.
- Expected actors: Manager, Technician, Requester depending on transition; matrix TBD.
- Dependencies: Ticket Reporting, Authentication/Authorization.
- Status: **Designed**. Vocabulary/documented workflow exists; no workflow implementation.

### Ticket Assignment and Work Logs

- Purpose: phân công technician, accept/end assignment và ghi nhận diagnosis/progress/repair/resolution.
- Related entities: `ticket_assignments`, `work_logs`, `technician_profiles`.
- Expected actors: Manager and Technician are likely; requires confirmation.
- Dependencies: Ticket, Technician Workforce, workflow rules.
- Status: **Designed** from database; no service/API/UI.

### Ticket Collaboration and Attachments

- Purpose: comments với public/internal visibility và evidence/work-result/comment attachments.
- Related entities: `ticket_comments`, `ticket_attachments`.
- Expected actors: TBD by visibility and ticket access policy.
- Dependencies: Ticket, User, attachment storage/security.
- Status: **Designed**; not implemented beyond database.

### Feedback

- Purpose: một đánh giá 1-5 và optional comment cho ticket.
- Related entities: `ticket_feedback`.
- Expected actors: Reporter according to column naming/documentation; service validation TBD.
- Dependencies: Ticket lifecycle and User.
- Status: **Designed for MVP**; no API/UI.

### SLA Management

- Purpose: reference policies by priority, resolution duration và optional pause-while-waiting setting.
- Related entities: `sla_policies`, `category_form_versions`, `tickets`.
- Expected actors: configuration ownership TBD.
- Dependencies: Category/Form and Ticket workflow.
- Status: **Designed** and seeded; runtime calculation not implemented.

### Dashboard and Reporting

- Purpose: workload, status distribution, turnaround time và SLA metrics.
- Related entities: projections over tickets, assignments, work logs, SLA and feedback.
- Expected actors: role-specific behavior TBD.
- Dependencies: operational ticket data and agreed metric definitions.
- Status: **Planned**. Dashboard UI is placeholder metrics only.

### Notifications

- Purpose: notification records associated with recipient and optional ticket.
- Related entities: `notifications`.
- Expected actors: all recipients depending on event matrix.
- Dependencies: User, Ticket, event definitions/channel strategy.
- Status: **Planned/optional** per architecture documentation; database only.

### Audit

- Purpose: record actor, action, target, before/after JSON and reason.
- Related entities: `audit_logs`.
- Expected actors: actor can be null for system events; readers TBD.
- Dependencies: authenticated operations and audit event policy.
- Status: **Planned/optional** per architecture documentation; database only.

### Reviewed Violation and Appeal

- Purpose: manually review alleged false reports/restrictions and possible appeals.
- Related entities: `violation_cases`, `violation_appeals`, `ticket_accuracy_reviews`.
- Expected actors: Manager is mentioned for opening/review; subject/submitting user exists; exact permissions TBD.
- Dependencies: resolved ticket evidence, users, review process and audit.
- Status: **Planned P1/optional**. Schema exists; not a current MVP implementation.

## Cross-Feature Dependencies

### Confirmed dependencies

```text
Role → User role membership
User → Technician profile → Technician skills / service areas
Location → Asset
Equipment type → Asset → Asset status history
Category → Form version → Field definition → Field option
User + Category/Form + Location (+ optional Asset) → Ticket
Ticket → Field values / assignment / history / work log / comment / attachment / feedback
Skill + SLA policy → Form version defaults → Ticket planning context
Ticket → Violation case → Appeal
```

### Likely dependencies requiring confirmation

- Real authentication before protected business screens.
- Category/form query, location query and asset query before ticket creation UI.
- Technician skill/service-area/capacity before assisted assignment.
- Agreed transitions before assignment, work log, resolution and feedback APIs.
- Stable operational data and metric definitions before dashboard completion.

## Feature Completion Rule

`frontend/src/features/README.md` xác định một feature chỉ hoàn chỉnh khi page, hook, API, backend endpoint, validation, authorization và tests đã nối thành end-to-end flow. Theo tiêu chí này, repository hiện chưa có business feature hoàn chỉnh.
