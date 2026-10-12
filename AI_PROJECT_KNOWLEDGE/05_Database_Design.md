# Database Design

> **Decision update (2026-09-25):** `00_Approved_Decisions.md` resolves the runtime precedence for asset requirement and SLA, assignment eligibility, dynamic JSON representation and ticket numbering. No database migration or source implementation has been performed by that decision alone.

## Scope and Source

- Database engine: Microsoft SQL Server 2022 Developer.
- Schema owner: Flyway migrations V1-V7 in `backend/src/main/resources/db/migration/`.
- Physical schema: `dbo`, 29 application tables.
- ID strategy: mainly `BIGINT IDENTITY(1,1)`; `roles.id` is `SMALLINT IDENTITY`; join tables use composite keys.
- Time strategy: `DATETIMEOFFSET(7)` with `SYSUTCDATETIME()` defaults for most creation/update timestamps.
- Hibernate setting: `ddl-auto=validate`; schema evolution must use new Flyway migrations.
- No FK declares cascade action; SQL Server default `NO ACTION` applies.

`infrastructure/sqlserver/nexora_full_schema.sql` is a standalone V1-V7 bundle, but Flyway migrations are the application source of truth.

## Tables by Domain

### `app_users`

Purpose: user identity/account record. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `email` | `NVARCHAR(254)` | No | Raw email |
| `normalized_email` | computed | No | persisted `LOWER(LTRIM(RTRIM(email)))` |
| `password_hash` | `NVARCHAR(100)` | No | No hashing algorithm specified |
| `full_name` | `NVARCHAR(120)` | No |  |
| `status` | `VARCHAR(20)` | No | `ACTIVE` |
| `version` | `BIGINT` | No | `0` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `updated_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Unique/index: unique index on `normalized_email`.
- Check/enum: `status ∈ {ACTIVE, LOCKED, DISABLED}`.
- Relationships: parent of roles, tickets and most actor/reference columns.

### `roles`

Purpose: fixed application role catalog. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `SMALLINT IDENTITY` | No | PK |
| `code` | `VARCHAR(30)` | No | Unique |
| `name` | `NVARCHAR(80)` | No |  |

- Check/enum: `code ∈ {REQUESTER, TECHNICIAN, MANAGER, ADMIN}`.
- Relationships: `roles 1 → N user_roles`.

### `user_roles`

Purpose: many-to-many user/role membership with assignment metadata. PK: (`user_id`, `role_id`).

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `user_id` | `BIGINT` | No | FK → `app_users.id` |
| `role_id` | `SMALLINT` | No | FK → `roles.id` |
| `assigned_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `assigned_by` | `BIGINT` | Yes | FK → `app_users.id` |

- Index: `role_id`.
- Relationships: users N ↔ N roles; assigning user is optional for system seed/migration.

### `locations`

Purpose: hierarchical campus locations. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `code` | `VARCHAR(50)` | No | Unique |
| `name` | `NVARCHAR(150)` | No |  |
| `location_type` | `VARCHAR(20)` | No | Enum |
| `parent_id` | `BIGINT` | Yes | Self FK → `locations.id` |
| `description` | `NVARCHAR(500)` | Yes |  |
| `active` | `BIT` | No | `1` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `updated_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Check/enum: type is `CAMPUS`, `BUILDING`, `FLOOR`, `ROOM`, `AREA`; direct self-parent is forbidden.
- Indexes: `parent_id`; (`active`, `location_type`).
- Relationships: self tree; parent of assets, tickets and technician service areas.

### `equipment_types`

Purpose: equipment/asset type catalog. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `code` | `VARCHAR(50)` | No | Unique |
| `name` | `NVARCHAR(150)` | No |  |
| `description` | `NVARCHAR(500)` | Yes |  |
| `brand` | `NVARCHAR(100)` | Yes | Added V6 |
| `model` | `NVARCHAR(100)` | Yes | Added V6 |
| `active` | `BIT` | No | `1` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `updated_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Relationships: `equipment_types 1 → N assets`.

### `assets`

Purpose: individual equipment assets and current status. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `asset_code` | `VARCHAR(80)` | No | Unique |
| `serial_number` | `NVARCHAR(120)` | Yes | Unique when non-null |
| `equipment_type_id` | `BIGINT` | No | FK → `equipment_types.id` |
| `location_id` | `BIGINT` | No | FK → `locations.id` |
| `asset_status` | `VARCHAR(30)` | No | `ACTIVE` |
| `description` | `NVARCHAR(500)` | Yes |  |
| `purchased_at` | `DATE` | Yes |  |
| `warranty_until` | `DATE` | Yes | Added V6 |
| `version` | `BIGINT` | No | `0` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `updated_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Check/enum: status is `ACTIVE`, `UNDER_MAINTENANCE`, `OUT_OF_SERVICE`, `RETIRED`.
- Indexes: `equipment_type_id`; (`location_id`, `asset_status`); filtered unique `serial_number IS NOT NULL`.
- Relationships: belongs to type/location; parent of asset history and optional ticket reference.

### `asset_status_history`

Purpose: immutable-style record of asset status changes, optionally linked to a ticket. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `asset_id` | `BIGINT` | No | FK → `assets.id` |
| `from_status` | `VARCHAR(30)` | Yes | Asset status enum |
| `to_status` | `VARCHAR(30)` | No | Asset status enum |
| `reason` | `NVARCHAR(500)` | No |  |
| `changed_by` | `BIGINT` | Yes | FK → `app_users.id` |
| `changed_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `ticket_id` | `BIGINT` | Yes | FK → `tickets.id` |

- Indexes: (`asset_id`, `changed_at DESC`); filtered (`ticket_id`, `changed_at DESC`).
- Relationships: child of asset; optional actor and originating ticket.

### `incident_categories`

Purpose: incident classification and category-level defaults. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `code` | `VARCHAR(50)` | No | Unique |
| `name` | `NVARCHAR(150)` | No |  |
| `description` | `NVARCHAR(1000)` | Yes |  |
| `asset_policy` | `VARCHAR(20)` | No | `OPTIONAL` |
| `location_required` | `BIT` | No | `1` |
| `default_priority` | `VARCHAR(20)` | No | `NORMAL` |
| `sla_hours` | `INT` | Yes | Must be positive if present |
| `active` | `BIT` | No | `1` |
| `created_by` | `BIGINT` | Yes | FK → `app_users.id` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `updated_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Enums: asset policy `REQUIRED/OPTIONAL/FORBIDDEN`; priority `LOW/NORMAL/HIGH/URGENT`.
- Index: (`active`, `name`).
- Relationships: category 1 → N form versions and tickets.

### `category_form_versions`

Purpose: versioned, publishable form definition for a category. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `category_id` | `BIGINT` | No | FK → `incident_categories.id` |
| `version_number` | `INT` | No | Unique per category |
| `form_status` | `VARCHAR(20)` | No | `DRAFT` |
| `created_by` | `BIGINT` | Yes | FK → user |
| `published_by` | `BIGINT` | Yes | FK → user |
| `requires_equipment` | `BIT` | No | `0` |
| `min_attachment_count` | `INT` | No | `0`, non-negative |
| `default_skill_id` | `BIGINT` | Yes | FK → `skills.id` |
| `default_sla_policy_id` | `BIGINT` | Yes | FK → `sla_policies.id` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `published_at` | `DATETIMEOFFSET(7)` | Yes | Required for published/archived |

- Unique: (`category_id`, `version_number`); (`category_id`, `id`) for composite FKs; filtered one published version per category.
- Check/enum: `DRAFT`, `PUBLISHED`, `ARCHIVED`; publish metadata consistency.
- Index: filtered `default_skill_id`.
- Relationships: belongs to category; parent of field definitions and tickets; optional default skill/SLA.

### `field_definitions`

Purpose: dynamic fields belonging to one form version. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `form_version_id` | `BIGINT` | No | FK → form version |
| `field_key` | `VARCHAR(80)` | No | Unique per form |
| `label` | `NVARCHAR(150)` | No |  |
| `field_type` | `VARCHAR(30)` | No | Enum |
| `help_text` | `NVARCHAR(500)` | Yes |  |
| `placeholder` | `NVARCHAR(250)` | Yes |  |
| `required` | `BIT` | No | `0` |
| `display_order` | `INT` | No | Non-negative |
| `validation_rules` | `NVARCHAR(MAX)` | No | `{}`, valid JSON object |

- Enums: `TEXT`, `TEXTAREA`, `NUMBER`, `SELECT`, `MULTI_SELECT`, `DATE`, `DATETIME`, `BOOLEAN`, `IMAGE`.
- Unique: (`form_version_id`, `field_key`); (`form_version_id`, `id`) for composite FK.
- Index: (`form_version_id`, `display_order`).
- Relationships: form version 1 → N definitions; definition 1 → N options/values/field attachments.

### `field_options`

Purpose: choices for a dynamic field. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `field_definition_id` | `BIGINT` | No | FK → field definition |
| `option_value` | `VARCHAR(100)` | No | Unique per field |
| `option_label` | `NVARCHAR(150)` | No |  |
| `display_order` | `INT` | No | Non-negative |
| `active` | `BIT` | No | `1` |

- Index: (`field_definition_id`, `display_order`).
- Relationships: child of field definition.

### `tickets`

Purpose: central incident/maintenance aggregate. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `ticket_number` | `VARCHAR(30)` | No | Unique |
| `reporter_id` | `BIGINT` | No | FK → user |
| `category_id` | `BIGINT` | No | FK → category |
| `form_version_id` | `BIGINT` | No | Composite FK with category |
| `location_id` | `BIGINT` | No | FK → location |
| `asset_id` | `BIGINT` | Yes | FK → asset |
| `duplicate_of_ticket_id` | `BIGINT` | Yes | Self FK |
| `required_skill_id` | `BIGINT` | Yes | FK → skill |
| `title` | `NVARCHAR(200)` | No |  |
| `description` | `NVARCHAR(MAX)` | No |  |
| `priority` | `VARCHAR(20)` | No | `NORMAL` |
| `ticket_status` | `VARCHAR(30)` | No | `SUBMITTED` |
| `resolution_outcome` | `VARCHAR(30)` | Yes | Resolution enum |
| `sla_due_at` | `DATETIMEOFFSET(7)` | Yes |  |
| `resolved_at` | `DATETIMEOFFSET(7)` | Yes | Lifecycle constrained |
| `closed_at` | `DATETIMEOFFSET(7)` | Yes | Lifecycle constrained |
| `version` | `BIGINT` | No | `0` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `updated_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Unique: `ticket_number`; (`id`, `form_version_id`) for child composite FKs.
- Status enum: `SUBMITTED`, `UNDER_REVIEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `REOPENED`, `CLOSED`, `REJECTED`, `CANCELLED`.
- Resolution enum: `FIXED`, `TEMPORARY_FIX`, `NO_FAULT_FOUND`, `DUPLICATE`, `UNRESOLVED` only for resolved/closed.
- Indexes: (`status`, `created_at DESC`), (`reporter_id`, `created_at DESC`), (`category_id`, `status`), (`location_id`, `status`), filtered asset/status, SLA/status/priority, required-skill/status.
- Relationships: central parent for dynamic values, assignment, history, work logs, attachments, comments, feedback, reviews, notifications and violation cases.

### `ticket_field_values`

Purpose: typed-at-service-layer dynamic answers stored as JSON values. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `ticket_id` | `BIGINT` | No | Composite FK with form version |
| `form_version_id` | `BIGINT` | No | Links ticket and field |
| `field_definition_id` | `BIGINT` | No | Composite FK with form version |
| `value_json` | `NVARCHAR(MAX)` | No | Valid JSON value |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Unique: (`ticket_id`, `field_definition_id`).
- Index: `ticket_id`.
- Relationships: bridges ticket to a field in the same form version.

### `ticket_assignments`

Purpose: assignment history with one active assignment per ticket. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `ticket_id` | `BIGINT` | No | FK → ticket |
| `technician_id` | `BIGINT` | No | FK → technician profile |
| `assigned_by` | `BIGINT` | No | FK → user |
| `assigned_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `accepted_at` | `DATETIMEOFFSET(7)` | Yes | Time constrained |
| `ended_at` | `DATETIMEOFFSET(7)` | Yes | Null means active |
| `end_reason` | `NVARCHAR(500)` | Yes |  |
| `override_reason` | `NVARCHAR(1000)` | Yes | Added V6 |

- Unique/index: filtered unique `ticket_id WHERE ended_at IS NULL`; (`technician_id`, `ended_at`, `assigned_at DESC`).
- Checks: accepted/end not before assigned; accepted not after end.

### `ticket_status_history`

Purpose: audit trail of ticket transitions. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `ticket_id` | `BIGINT` | No | FK → ticket |
| `from_status` | `VARCHAR(30)` | Yes | Ticket status enum |
| `to_status` | `VARCHAR(30)` | No | Ticket status enum |
| `transition_action` | `VARCHAR(40)` | No | No DB enum |
| `reason` | `NVARCHAR(1000)` | Yes |  |
| `changed_by` | `BIGINT` | No | FK → user |
| `changed_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Index: (`ticket_id`, `changed_at DESC`).
- Checks: only status vocabulary, not allowed transition pairs.

### `work_logs`

Purpose: technician work records for a ticket. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `ticket_id` | `BIGINT` | No | FK → ticket |
| `technician_id` | `BIGINT` | No | FK → technician profile |
| `work_description` | `NVARCHAR(MAX)` | No |  |
| `started_at` | `DATETIMEOFFSET(7)` | No |  |
| `ended_at` | `DATETIMEOFFSET(7)` | Yes | Not before start |
| `minutes_spent` | `INT` | Yes | Positive if present |
| `log_type` | `VARCHAR(30)` | No | `PROGRESS` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Enum: `DIAGNOSIS`, `PROGRESS`, `REPAIR`, `RESOLUTION`, `NO_FAULT_FOUND`.
- Index: (`ticket_id`, `created_at DESC`).

### `ticket_attachments`

Purpose: stored-file metadata for ticket evidence/results/comments or dynamic image fields. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `ticket_id` | `BIGINT` | No | FK → ticket |
| `form_version_id` | `BIGINT` | No | Composite consistency FK |
| `field_definition_id` | `BIGINT` | Yes | Optional dynamic field FK |
| `uploaded_by` | `BIGINT` | No | FK → user |
| `attachment_type` | `VARCHAR(30)` | No | Enum |
| `storage_key` | `NVARCHAR(500)` | No | Unique |
| `original_name` | `NVARCHAR(255)` | No |  |
| `content_type` | `VARCHAR(120)` | No |  |
| `size_bytes` | `BIGINT` | No | Positive |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Enum: `REPORT_EVIDENCE`, `WORK_RESULT`, `COMMENT`.
- Index: (`ticket_id`, `created_at DESC`).
- Relationships: ticket/form pair must match; field, when present, must belong to same form.

### `ticket_comments`

Purpose: public/internal ticket discussion. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `ticket_id` | `BIGINT` | No | FK → ticket |
| `author_id` | `BIGINT` | No | FK → user |
| `body` | `NVARCHAR(2000)` | No |  |
| `visibility` | `VARCHAR(20)` | No | `PUBLIC` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `edited_at` | `DATETIMEOFFSET(7)` | Yes | Not before create |

- Enum: `PUBLIC`, `INTERNAL`.
- Index: (`ticket_id`, `created_at ASC`).

### `ticket_feedback`

Purpose: one satisfaction response per ticket. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `ticket_id` | `BIGINT` | No | Unique FK → ticket |
| `reporter_id` | `BIGINT` | No | FK → user |
| `rating` | `TINYINT` | No | 1-5 |
| `comment` | `NVARCHAR(1000)` | Yes |  |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Relationship: ticket 1 → 0..1 feedback; DB does not prove reporter identity matches ticket reporter.

### `ticket_accuracy_reviews`

Purpose: record reviews of report accuracy. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `ticket_id` | `BIGINT` | No | FK → ticket |
| `review_result` | `VARCHAR(40)` | No | Enum |
| `reason` | `NVARCHAR(1000)` | No |  |
| `reviewed_by` | `BIGINT` | No | FK → user |
| `reviewed_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Enum: `ACCURATE`, `INACCURATE`, `INSUFFICIENT_EVIDENCE`, `NO_FAULT_FOUND`.
- Index: (`ticket_id`, `reviewed_at DESC`).
- Relationship: ticket 1 → N reviews.

### `notifications`

Purpose: in-database notifications for a recipient and optional ticket. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `recipient_id` | `BIGINT` | No | FK → user |
| `ticket_id` | `BIGINT` | Yes | FK → ticket |
| `notification_type` | `VARCHAR(40)` | No | No DB enum |
| `title` | `NVARCHAR(200)` | No |  |
| `message` | `NVARCHAR(1000)` | No |  |
| `read_at` | `DATETIMEOFFSET(7)` | Yes | Null means unread |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Index: filtered (`recipient_id`, `created_at DESC`) for unread rows.

### `technician_profiles`

Purpose: technician-specific availability and capacity. PK/FK: `technician_id` → `app_users.id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `technician_id` | `BIGINT` | No | PK and FK |
| `max_active_tickets` | `INT` | No | `5`, positive |
| `available` | `BIT` | No | `1` |
| `version` | `BIGINT` | No | `0` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `updated_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Relationships: user 1 → 0..1 profile; profile parent of skills, service areas, assignments and work logs.

### `skills`

Purpose: technician/incident skill catalog. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `code` | `VARCHAR(50)` | No | Unique |
| `name` | `NVARCHAR(150)` | No |  |
| `active` | `BIT` | No | `1` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `updated_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Relationships: N ↔ N technicians; optional default on forms and requirement on tickets.

### `technician_skills`

Purpose: technician skill and proficiency mapping. PK: (`technician_id`, `skill_id`).

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `technician_id` | `BIGINT` | No | FK → profile |
| `skill_id` | `BIGINT` | No | FK → skill |
| `proficiency_level` | `TINYINT` | No | 1-5 |
| `assigned_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Index: (`skill_id`, `proficiency_level DESC`).

### `technician_service_areas`

Purpose: technician-to-location service coverage. PK: (`technician_id`, `location_id`).

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `technician_id` | `BIGINT` | No | FK → profile |
| `location_id` | `BIGINT` | No | FK → location |

- Index: (`location_id`, `technician_id`).

### `sla_policies`

Purpose: reusable SLA reference policies. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `policy_name` | `NVARCHAR(150)` | No | Unique |
| `priority` | `VARCHAR(20)` | No | Priority enum |
| `resolution_minutes` | `INT` | No | Positive |
| `pause_waiting_requester` | `BIT` | No | `0` |
| `active` | `BIT` | No | `1` |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `updated_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Relationship: policy 1 → N category form versions.

### `audit_logs`

Purpose: generic operation audit record. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `actor_id` | `BIGINT` | Yes | FK → user; null permits system actor |
| `action` | `VARCHAR(100)` | No | No DB enum |
| `target_type` | `VARCHAR(80)` | No | Polymorphic label |
| `target_id` | `BIGINT` | No | No FK due polymorphic target |
| `before_data` | `NVARCHAR(MAX)` | Yes | Valid JSON if present |
| `after_data` | `NVARCHAR(MAX)` | Yes | Valid JSON if present |
| `reason` | `NVARCHAR(1000)` | Yes |  |
| `created_at` | `DATETIMEOFFSET(7)` | No | UTC now |

- Indexes: (`target_type`, `target_id`, `created_at DESC`); filtered actor/time.

### `violation_cases`

Purpose: reviewed case associated with one ticket and a subject user. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `ticket_id` | `BIGINT` | No | Unique FK → ticket |
| `subject_user_id` | `BIGINT` | No | FK → user |
| `opened_by` | `BIGINT` | No | FK → user |
| `review_status` | `VARCHAR(30)` | No | `OPEN` |
| `allegation_reason` | `NVARCHAR(2000)` | No |  |
| `decision_reason` | `NVARCHAR(2000)` | Yes | Required by decided status |
| `reviewed_by` | `BIGINT` | Yes | FK → user |
| `restriction_until` | `DATETIMEOFFSET(7)` | Yes |  |
| `opened_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `reviewed_at` | `DATETIMEOFFSET(7)` | Yes | Status constrained |

- Enum: `OPEN`, `UNDER_REVIEW`, `CONFIRMED`, `DISMISSED`, `RESTRICTED`.
- Index: (`subject_user_id`, `review_status`, `opened_at DESC`).

### `violation_appeals`

Purpose: appeals for violation cases. PK: `id`.

| Field | Type | Nullable | Default / notes |
|---|---|---:|---|
| `id` | `BIGINT IDENTITY` | No | PK |
| `violation_case_id` | `BIGINT` | No | FK → violation case |
| `submitted_by` | `BIGINT` | No | FK → user |
| `appeal_text` | `NVARCHAR(2000)` | No |  |
| `appeal_status` | `VARCHAR(30)` | No | `SUBMITTED` |
| `reviewed_by` | `BIGINT` | Yes | FK → user |
| `decision_note` | `NVARCHAR(2000)` | Yes | Required for approved/rejected |
| `submitted_at` | `DATETIMEOFFSET(7)` | No | UTC now |
| `reviewed_at` | `DATETIMEOFFSET(7)` | Yes | Status constrained |

- Enum: `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `WITHDRAWN`.
- Index: (`violation_case_id`, `submitted_at DESC`).
- Relationship: violation case 1 → N appeals; no limit on concurrent appeals in DB.

## Entity Relationship Summary

- `app_users N ↔ N roles` through `user_roles`.
- `app_users 1 → 0..1 technician_profiles`.
- `technician_profiles N ↔ N skills` through `technician_skills`.
- `technician_profiles N ↔ N locations` through `technician_service_areas`.
- `locations 1 → N locations` through `parent_id`.
- `equipment_types 1 → N assets`; `locations 1 → N assets`.
- `assets 1 → N asset_status_history`; a history row may reference one ticket.
- `incident_categories 1 → N category_form_versions` with at most one published version.
- `category_form_versions 1 → N field_definitions 1 → N field_options`.
- `category_form_versions N → 0..1 skills` and `N → 0..1 sla_policies` as defaults.
- `app_users`, `incident_categories/category_form_versions` and `locations` each have `1 → N tickets`; asset and required skill are optional ticket parents.
- `tickets 1 → N` field values, assignments, status history, work logs, attachments, comments, accuracy reviews and notifications.
- `tickets 1 → 0..1 ticket_feedback` and `1 → 0..1 violation_cases`.
- `violation_cases 1 → N violation_appeals`.

## Domain Dependency Graph

### Confirmed database dependencies

```text
roles ──→ user_roles ←── app_users
                         │
                         ├──→ technician_profiles ──→ technician_skills ←── skills
                         │             │
locations ───────────────┼─────────────└──→ technician_service_areas
    │                    │
    └──→ assets ←── equipment_types
           │
           └──→ asset_status_history ←── tickets

incident_categories ──→ category_form_versions ──→ field_definitions ──→ field_options
                              │     │                       │
skills ───────────────────────┘     │                       │
sla_policies ───────────────────────┘                       │
                                                           │
app_users + category/form + location + optional asset/skill ──→ tickets
                                                                │
                                                                ├──→ ticket_field_values
                                                                ├──→ ticket_assignments
                                                                ├──→ ticket_status_history
                                                                ├──→ work_logs
                                                                ├──→ ticket_attachments
                                                                ├──→ ticket_comments
                                                                ├──→ ticket_feedback
                                                                ├──→ ticket_accuracy_reviews
                                                                ├──→ notifications
                                                                └──→ violation_cases ──→ violation_appeals
```

### Likely service-level dependencies

- Category/form validation before creating ticket.
- Role/technician-profile validation before assignment.
- Skill/service-area/capacity evaluation before assignment.
- Ticket state policy before work log, resolution, close, feedback or violation operations.
- Attachment storage before metadata can be useful.

These are **Likely - requires confirmation/implementation** unless explicitly documented as a rule.

## Seed Data

| Group | Seeded values |
|---|---|
| Roles | `REQUESTER`, `TECHNICIAN`, `MANAGER`, `ADMIN` |
| Location | Campus Main → Building Alpha → Floor 1 → Rooms A101/A102 |
| Equipment types | Projector, Air Conditioner |
| Assets | One sample projector in A101; one sample AC in A102 |
| Categories | Projector issue, air-conditioner issue, water leak, safety incident |
| Skills | Projector, HVAC, Plumbing, Safety |
| SLA | Low 72h, Normal 24h, High 4h, Urgent 1h |
| Forms | One published starter version per seeded category, with category-specific fields/default skill/default SLA |

Seed data is development/reference configuration, not evidence that corresponding business workflows are implemented.

## Database Observations

1. Schema preserves the category form version used by each ticket and uses composite FKs to keep dynamic values/attachments aligned with that version.
2. Optimistic-lock-style `version` columns exist on users, assets, tickets and technician profiles, but no JPA mapping exists yet.
3. Filtered unique indexes express one published form per category, one active assignment per ticket and nullable unique serial numbers.
4. Ticket status history and asset status history are modeled separately from current status fields.
5. SLA exists in two representations: legacy `incident_categories.sla_hours` and reusable `sla_policies` referenced by form version.
6. Asset requirement also exists in two representations: category `asset_policy` and form-version `requires_equipment`.
7. `value_json` is database-validated JSON, while semantic field validation is intentionally delegated to backend code.
8. Violation and appeal tables are physically present even though repository docs classify them as P1/optional.

## Potential Database Gaps / Clarifications

These are observations only; no database change is proposed or performed.

- `locations` prevents only direct self-parenting, not longer cycles or invalid type hierarchy such as ROOM → CAMPUS.
- Database does not enforce that `technician_profiles` users have role `TECHNICIAN`.
- Database does not enforce category asset policy/form `requires_equipment`, location/asset consistency or active-state eligibility when creating a ticket.
- Relationship/precedence between `incident_categories.sla_hours` and `category_form_versions.default_sla_policy_id` is unclear.
- Relationship/precedence between `asset_policy` and `requires_equipment` is unclear.
- Database does not enforce the allowed ticket transition graph, actor permissions or synchronization between current status and status history.
- Resolved/closed status does not require `resolved_at`/`closed_at`; constraints only restrict timestamps when present.
- Feedback reporter is not constrained to equal ticket reporter, and feedback eligibility by status is not enforced.
- `ticket_accuracy_reviews` permits multiple reviews with no current/final marker.
- `notification_type`, audit `action/target_type` and transition `transition_action` have no controlled database vocabulary.
- Violation subject is not constrained to ticket reporter; appeal concurrency/count and submitter eligibility are not constrained.
- No attachment blob/object storage implementation or referential link to a comment exists; `attachment_type=COMMENT` alone does not identify a comment.
