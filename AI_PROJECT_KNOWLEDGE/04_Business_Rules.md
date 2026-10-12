# Business Rules

> **Decision update (2026-09-25):** business rules còn thiếu về roles, workflow, ticket creation, assignment, SLA, feedback, comments và attachments đã được chốt trong `00_Approved_Decisions.md`. Các `DEC-*` là approved rules; BR trong file này tiếp tục mô tả evidence ban đầu từ database/docs.

Các rule dưới đây chỉ đến từ database constraints, seed/config hoặc tài liệu hiện có. Rule enforced bằng database được ghi `Implemented in database`; rule chỉ xuất hiện trong tài liệu được ghi `Designed`.

## Implemented Database Rules

### Identity and Roles

- **BR-001**: User email sau khi trim và lowercase phải unique. Source: persisted `normalized_email` + unique index.
- **BR-002**: User status chỉ là `ACTIVE`, `LOCKED`, `DISABLED`; mặc định `ACTIVE`. Source: `app_users` check/default.
- **BR-003**: Role code chỉ là `REQUESTER`, `TECHNICIAN`, `MANAGER`, `ADMIN` và phải unique. Source: `roles` constraints.
- **BR-004**: Một user-role pair chỉ tồn tại một lần. Source: composite PK `user_roles(user_id, role_id)`.

### Locations and Assets

- **BR-005**: Location code phải unique; type chỉ là `CAMPUS`, `BUILDING`, `FLOOR`, `ROOM`, `AREA`. Source: `locations` constraints.
- **BR-006**: Location không được trực tiếp làm parent của chính nó. Source: `ck_locations_not_self_parent`.
- **BR-007**: Asset code phải unique; serial number, nếu có, phải unique. Source: `uq_assets_asset_code`, filtered `uq_assets_serial_number`.
- **BR-008**: Asset phải thuộc một equipment type và một location. Source: non-null FKs.
- **BR-009**: Asset status chỉ là `ACTIVE`, `UNDER_MAINTENANCE`, `OUT_OF_SERVICE`, `RETIRED`; mặc định `ACTIVE`. Source: `assets` constraints.
- **BR-010**: Asset status history phải có reason; `to_status` hợp lệ, `from_status` null hoặc hợp lệ. Source: `asset_status_history` constraints.

### Categories and Dynamic Forms

- **BR-011**: Category code phải unique. Source: `incident_categories` unique constraint.
- **BR-012**: Category asset policy chỉ là `REQUIRED`, `OPTIONAL`, `FORBIDDEN`; priority chỉ là `LOW`, `NORMAL`, `HIGH`, `URGENT`; `sla_hours` nếu có phải lớn hơn 0. Source: checks.
- **BR-013**: Version number phải unique trong một category; mỗi category tối đa một form version `PUBLISHED`. Source: unique constraint và filtered unique index.
- **BR-014**: Form status chỉ là `DRAFT`, `PUBLISHED`, `ARCHIVED`. Draft không có `published_at`; published/archived phải có `published_at`. Source: `category_form_versions` checks.
- **BR-015**: `min_attachment_count` không âm. Source: V6 check constraint.
- **BR-016**: Field key phải unique trong một form version; display order không âm; validation rules phải là JSON object hợp lệ. Source: `field_definitions` constraints.
- **BR-017**: Dynamic field type chỉ là `TEXT`, `TEXTAREA`, `NUMBER`, `SELECT`, `MULTI_SELECT`, `DATE`, `DATETIME`, `BOOLEAN`, `IMAGE`. Source: `field_definitions.field_type` check.
- **BR-018**: Field option value phải unique trong một field; display order không âm. Source: `field_options` constraints.

### Tickets and Workflow Data

- **BR-019**: Ticket number phải unique. Source: `tickets` unique constraint.
- **BR-020**: Ticket phải có reporter, category/form version hợp lệ và location; asset là nullable. Source: non-null columns và FKs.
- **BR-021**: Ticket category và form version phải thuộc cùng pair đã đăng ký. Source: composite FK `fk_tickets_category_form`.
- **BR-022**: Ticket priority chỉ là `LOW`, `NORMAL`, `HIGH`, `URGENT`; mặc định `NORMAL`. Source: check/default.
- **BR-023**: Ticket status chỉ là `SUBMITTED`, `UNDER_REVIEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `REOPENED`, `CLOSED`, `REJECTED`, `CANCELLED`; mặc định `SUBMITTED`. Source: check/default.
- **BR-024**: Ticket không được đánh dấu duplicate của chính nó. Source: `ck_tickets_not_self_duplicate`.
- **BR-025**: `resolved_at`, nếu có, chỉ hợp lệ khi status là `RESOLVED` hoặc `CLOSED`; `closed_at`, nếu có, chỉ hợp lệ khi status là `CLOSED`. Source: lifecycle timestamp check.
- **BR-026**: `resolution_outcome`, nếu có, chỉ là `FIXED`, `TEMPORARY_FIX`, `NO_FAULT_FOUND`, `DUPLICATE`, `UNRESOLVED` và chỉ hợp lệ khi ticket `RESOLVED`/`CLOSED`. Source: V6 check.
- **BR-027**: Mỗi ticket-field pair tối đa một dynamic value; ticket, field và form version phải nhất quán; value phải là JSON value hợp lệ. Source: `ticket_field_values` unique/composite FKs/check.
- **BR-028**: Mỗi ticket tối đa một active assignment (`ended_at IS NULL`). Source: filtered unique index.
- **BR-029**: Assignment acceptance/end không trước assignment; nếu cả accepted/end tồn tại thì acceptance không sau end. Source: assignment time checks.
- **BR-030**: Work log end không trước start; `minutes_spent`, nếu có, phải dương. Source: `work_logs` checks.
- **BR-031**: Work log type chỉ là `DIAGNOSIS`, `PROGRESS`, `REPAIR`, `RESOLUTION`, `NO_FAULT_FOUND`; mặc định `PROGRESS`. Source: V6 check/default.
- **BR-032**: Attachment storage key phải unique, size phải dương, type chỉ là `REPORT_EVIDENCE`, `WORK_RESULT`, `COMMENT`; field attachment phải nhất quán với ticket form version. Source: attachment constraints/composite FKs.
- **BR-033**: Comment visibility chỉ là `PUBLIC` hoặc `INTERNAL`; edit time không trước create time. Source: comment constraints.
- **BR-034**: Một ticket tối đa một feedback; rating từ 1 đến 5. Source: feedback unique/check.
- **BR-035**: Accuracy review result chỉ là `ACCURATE`, `INACCURATE`, `INSUFFICIENT_EVIDENCE`, `NO_FAULT_FOUND`. Source: check.

### Workforce, SLA, Audit and Violation

- **BR-036**: Technician maximum active tickets phải lớn hơn 0; mặc định 5. Source: `technician_profiles` check/default.
- **BR-037**: Technician proficiency phải từ 1 đến 5; một technician-skill pair là unique. Source: `technician_skills` PK/check.
- **BR-038**: Một technician-service-area pair là unique. Source: composite PK.
- **BR-039**: SLA policy name unique; priority thuộc vocabulary chuẩn; resolution minutes phải dương. Source: `sla_policies` constraints.
- **BR-040**: Audit before/after data, nếu có, phải là JSON hợp lệ. Source: `audit_logs` checks.
- **BR-041**: Mỗi ticket tối đa một violation case. Source: `violation_cases.ticket_id` unique.
- **BR-042**: Violation status chỉ là `OPEN`, `UNDER_REVIEW`, `CONFIRMED`, `DISMISSED`, `RESTRICTED`. Trạng thái đã quyết định cần reviewer, reviewed time và decision reason; open/under-review chưa có reviewed time. Source: violation checks.
- **BR-043**: Appeal status chỉ là `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `WITHDRAWN`. Approved/rejected cần reviewer, reviewed time và decision note. Source: appeal checks.

## Documented Rules Not Yet Enforced by Business Code

- **BR-044**: Published category form versions are immutable; thay đổi form cần version mới. Status: **Designed**. Source: migration README và architecture docs.
- **BR-045**: Backend là authority cho permissions, dynamic validation và ticket transitions. Status: **Designed**. Source: root README.
- **BR-046**: Dynamic value phải được backend kiểm tra type, required, min/max và option theo `field_definitions.validation_rules`. Status: **Designed**. Source: `docs/DATABASE_SCHEMA.md`.
- **BR-047**: Reopen một resolved ticket cần clear current `resolved_at`, trong khi history cũ vẫn giữ trong `ticket_status_history`. Status: **Designed**. Source: `docs/ARCHITECTURE.md`.
- **BR-048**: Không cung cấp generic status endpoint cho client tự đặt bất kỳ status nào; transition phải là policy code được test. Status: **Designed**. Source: `docs/ARCHITECTURE.md`.
- **BR-049**: `NO_FAULT_FOUND` không tự động tạo violation; manager phải chủ động mở case và xem xét bằng chứng. Status: **Designed**. Source: V6 comments, `docs/DATABASE_SCHEMA.md`.
- **BR-050**: Feedback phải do đúng reporter tạo và assignee phải có role `TECHNICIAN` là các rule cần service/integration test; database không enforce hoàn toàn. Status: **Designed but not implemented**. Source: `docs/DATABASE_SCHEMA.md`.

## Seed/Configuration Facts

- Bốn roles được seed: requester, technician, manager, admin.
- Sample location hierarchy: `CAMPUS-MAIN → BLD-ALPHA → FL-ALPHA-1 → ROOM-A101/ROOM-A102`.
- Equipment types: projector và air conditioner; mỗi loại có một sample asset.
- Starter categories: projector issue, air-conditioner issue, water leak và safety incident.
- Skills: projector, HVAC, plumbing, safety.
- SLA policies: low 72h, normal 24h, high 4h, urgent 1h; low/normal seed cấu hình pause waiting requester, high/urgent không pause.
- Starter form rules/fields là development reference data, không được mặc định coi là toàn bộ approved business catalog.

## Business Rules Requiring Clarification

1. Role/permission matrix cho mọi ticket action, admin action và data visibility.
2. Allowed status-transition matrix, required reason/evidence và concurrency behavior.
3. Ticket-number generation và duplicate-detection policy.
4. Khi nào category-level `asset_policy` và form-level `requires_equipment` được ưu tiên; hiện có hai nguồn thể hiện yêu cầu thiết bị.
5. Quy tắc validate asset có thuộc location/category phù hợp không.
6. Assignment algorithm, capacity counting, skill/service-area matching và override approval.
7. SLA start/pause/resume/breach/escalation và xử lý policy thay đổi.
8. Feedback eligibility: ticket status nào, deadline, ai được sửa/xóa.
9. Comment `PUBLIC` được nhìn bởi ai; `INTERNAL` được nhìn bởi role nào.
10. Attachment limits, storage, security scan, retention và deletion.
11. Notification event matrix và delivery channel.
12. Violation/restriction effect và appeal lifecycle nếu P1 được đưa vào scope.
