# Requirements

> **Decision update (2026-09-25):** các requirement previously marked `Inferred`/`TBD` được coi là đã quyết định khi có `DEC-*` tương ứng trong `00_Approved_Decisions.md`. Decision record có precedence; file này vẫn giữ lịch sử evidence ban đầu.

## Classification Rules

- Một requirement chỉ nằm trong **Confirmed Requirements** khi có tuyên bố rõ trong tài liệu/source hiện tại.
- Requirement suy ra từ bảng, khóa ngoại hoặc scaffold nằm trong **Inferred Requirements** và bắt buộc cần xác nhận.
- `Implemented` trong file này chỉ mô tả mức hiện thực hóa; requirement được ghi trong tài liệu không tự động có nghĩa là feature đã code.

## Confirmed Requirements

### FR-001 - Campus incident reporting platform

Nexora phải phục vụ việc báo cáo sự cố campus, workflow bảo trì và theo dõi thiết bị.

- Evidence: `README.md`, backend Maven description, OpenAPI description.
- Requirement status: Confirmed by repository documentation.
- Implementation status: Designed; chưa có end-to-end business feature.

### FR-002 - React SPA integrated with Spring Boot REST API

Hệ thống được tổ chức thành một React SPA và một Spring Boot REST API trong modular monolith.

- Evidence: `README.md`, `docs/ARCHITECTURE.md`, `docs/PROJECT_STRUCTURE_SBA301.md`.
- Requirement status: Confirmed.
- Implementation status: Technical foundation implemented; business integration not implemented.

### FR-003 - Role model

Hệ thống có bốn role database: `REQUESTER`, `TECHNICIAN`, `MANAGER`, `ADMIN`.

- Evidence: `roles.code` check constraint và V5 seed data.
- Requirement status: Confirmed at database-design level.
- Implementation status: Implemented in database; authorization matrix TBD.

### FR-004 - Versioned dynamic category forms

Category có form versions; ticket lưu đúng form version được sử dụng để dữ liệu lịch sử vẫn đọc được sau khi category thay đổi. Published form version được tài liệu quy định là immutable.

- Evidence: V3/V4 constraints, `docs/ARCHITECTURE.md`, migration README.
- Requirement status: Confirmed.
- Implementation status: Database implemented; backend validation/publishing workflow not implemented.

### FR-005 - Ticket maintenance workflow

MVP được tài liệu mô tả theo luồng khuyến nghị `SUBMITTED → UNDER_REVIEW → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED`, cùng `REJECTED` và `CANCELLED`. Database còn cho phép `REOPENED`.

- Evidence: `docs/ARCHITECTURE.md`, `tickets.ticket_status` constraint.
- Requirement status: Confirmed as documented design; transition authorization/details chưa được chốt.
- Implementation status: Status vocabulary implemented in database only; workflow code not implemented.

### FR-006 - Asset and location tracking

Hệ thống thiết kế để quản lý location hierarchy, equipment types, assets và asset status history.

- Evidence: V2/V6 migrations, `README.md`, architecture docs.
- Requirement status: Confirmed at domain-design level.
- Implementation status: Database implemented; API/UI not implemented.

### FR-007 - Backend authority

Backend là nơi cuối cùng thực thi permission, dynamic validation và status transition; controller không chứa business decision và entity không được trả trực tiếp qua API.

- Evidence: `README.md`, `docs/ARCHITECTURE.md`, `docs/PROJECT_STRUCTURE_SBA301.md`.
- Requirement status: Confirmed architecture requirement.
- Implementation status: Foundation exists; business enforcement not implemented.

### FR-008 - Feedback belongs to MVP ticket module

Feedback được đặt trong ticket module cho MVP.

- Evidence: `docs/ARCHITECTURE.md`; table `ticket_feedback`.
- Requirement status: Confirmed in repository documentation.
- Implementation status: Database implemented; API/UI not implemented.

### FR-009 - Optional/P1 scope separation

Notification, audit và reviewed false-report functionality chỉ nên được thêm khi team thực sự triển khai; `violation_cases` và `violation_appeals` được mô tả là P1, có schema nhưng có thể không có API/UI trong MVP.

- Evidence: `docs/ARCHITECTURE.md`, `docs/DATABASE_SCHEMA.md`.
- Requirement status: Confirmed scope note.
- Implementation status: Database designed/implemented; business features not implemented.

## Inferred Requirements

### FR-010 - Ticket must retain contextual references

Ticket phải gắn reporter, category/form version và location; asset là tùy category/use case.

- **Status: Inferred - requires confirmation.**
- Evidence: non-null FKs on `tickets`; nullable `asset_id`; category asset policy.

### FR-011 - Technician matching may use skill, service area and capacity

Việc chọn kỹ thuật viên có khả năng dựa trên skill, service area, availability và maximum active tickets.

- **Status: Inferred - requires confirmation.**
- Evidence: `technician_profiles`, `technician_skills`, `technician_service_areas`, `tickets.required_skill_id`.
- Không đủ bằng chứng để xác định thuật toán auto-assignment hay quyền override.

### FR-012 - SLA due time may be derived from form policy

Ticket có thể nhận SLA dựa trên policy gắn ở form version và priority.

- **Status: Inferred - requires confirmation.**
- Evidence: `category_form_versions.default_sla_policy_id`, `sla_policies`, `tickets.sla_due_at`.
- Cách snapshot, pause/resume và recalculation là TBD.

### FR-013 - Ticket evidence and dynamic values must match form version

Dynamic values và attachment gắn field phải thuộc cùng form version với ticket.

- **Status: Inferred - requires confirmation of service behavior.**
- Evidence: composite foreign keys trong `ticket_field_values` và `ticket_attachments`.

### FR-014 - A ticket has at most one active assignment

- **Status: Inferred - requires confirmation.**
- Evidence: filtered unique index `uq_ticket_assignments_one_active`.

### FR-015 - Ticket feedback is one per ticket

- **Status: Inferred - requires confirmation.**
- Evidence: unique constraint on `ticket_feedback.ticket_id`.
- Database chưa bảo đảm feedback reporter là reporter của ticket hoặc ticket phải ở trạng thái nào.

### FR-016 - Reviewed false-report cases require explicit manager action

`NO_FAULT_FOUND` không tự động tạo violation; case phải được mở và review có bằng chứng.

- **Status: Inferred/Documented - requires confirmation of actor permissions.**
- Evidence: V6 comments và `docs/DATABASE_SCHEMA.md`.

## TBD Requirements

### FR-017 - Authentication lifecycle

TBD: login method, password policy, token issuance/refresh/revocation, account creation, reset password và session duration.

### FR-018 - Authorization matrix

TBD: actor nào được create/view/update/review/assign/resolve/close/cancel/reopen ticket và phạm vi dữ liệu từng actor được xem.

### FR-019 - Exact ticket transition policy

TBD: allowed transitions, initiating roles, required reasons, timestamp handling và rules cho `REOPENED`, `REJECTED`, `CANCELLED`.

### FR-020 - Ticket creation rules

TBD: ticket-number format, title/description validation, category/location/asset validation và duplicate-detection behavior.

### FR-021 - Category publishing lifecycle

TBD: ai tạo/publish/archive, khi nào draft hợp lệ, có được clone version hay không, và cách xử lý form đang được dùng.

### FR-022 - Assignment policy

TBD: manual hay automatic assignment, capacity calculation, skill/service-area matching, acceptance flow và override rule.

### FR-023 - SLA calculation

TBD: start time, business/calendar time, pause conditions, breach behavior, escalation và policy snapshot strategy.

### FR-024 - Attachment storage/security

TBD: storage provider, upload/download authorization, file-size/type limits toàn cục, antivirus/retention policy và signed URL behavior.

### FR-025 - Notification channels

TBD: in-app/email/other channels, event matrix, retry, read/unread và user preferences.

### FR-026 - Dashboard/reporting requirements

TBD: metrics, filters, date boundaries, actor-specific views và SLA definitions.

### FR-027 - False-report/violation scope

TBD: có thuộc MVP không, ai mở/review case, restriction effect, appeal limits và audit requirements.

### FR-028 - Non-functional requirements

TBD: performance, availability, accessibility, security baseline, backup/restore, retention, audit retention, localization và browser/device support.

### FR-029 - Acceptance and release criteria

TBD: Definition of Done, review policy, minimum tests, demo scenarios và deployment environment.
