/*
 Nexora - complete standalone SQL Server schema (Flyway V1 through V8)

 Target: Microsoft SQL Server 2022
 Database: nexora

 Run this file once on a new or empty database from SSMS or sqlcmd.
 The Spring Boot application still uses the original Flyway migration files as
 the authoritative schema source. Do not run this bundle on a database already
 managed by Flyway.
*/

USE [master];
GO

IF DB_ID(N'nexora') IS NULL
BEGIN
    EXEC(N'CREATE DATABASE [nexora]');
END;
GO

USE [nexora];
GO

-- Required by SQL Server for indexes on computed columns and filtered indexes.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
GO

IF EXISTS (
    SELECT 1
    FROM sys.tables
    WHERE schema_id = SCHEMA_ID(N'dbo')
)
    THROW 51010, 'nexora_full_schema.sql must run on an empty database.', 1;
GO

/* ===== BEGIN V1__create_user_and_role_tables.sql ===== */
CREATE TABLE dbo.app_users (
    id BIGINT IDENTITY(1,1) NOT NULL,
    email NVARCHAR(254) NOT NULL,
    normalized_email AS LOWER(LTRIM(RTRIM(email))) PERSISTED,
    password_hash NVARCHAR(100) NOT NULL,
    full_name NVARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL
        CONSTRAINT df_app_users_status DEFAULT 'ACTIVE',
    version BIGINT NOT NULL
        CONSTRAINT df_app_users_version DEFAULT 0,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_app_users_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_app_users_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT ck_app_users_status
        CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_app_users_normalized_email
    ON dbo.app_users (normalized_email);

CREATE TABLE dbo.roles (
    id SMALLINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(30) NOT NULL,
    name NVARCHAR(80) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code
        CHECK (code IN ('REQUESTER', 'TECHNICIAN', 'MANAGER', 'ADMIN'))
);

CREATE TABLE dbo.user_roles (
    user_id BIGINT NOT NULL,
    role_id SMALLINT NOT NULL,
    assigned_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_user_roles_assigned_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES dbo.app_users(id),
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id) REFERENCES dbo.roles(id)
);

CREATE INDEX idx_user_roles_role_id ON dbo.user_roles (role_id);


GO

/* ===== END V1__create_user_and_role_tables.sql ===== */

/* ===== BEGIN V2__create_location_and_asset_tables.sql ===== */
CREATE TABLE dbo.locations (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name NVARCHAR(150) NOT NULL,
    location_type VARCHAR(20) NOT NULL,
    parent_id BIGINT NULL,
    description NVARCHAR(500) NULL,
    active BIT NOT NULL
        CONSTRAINT df_locations_active DEFAULT 1,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_locations_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_locations_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_locations PRIMARY KEY (id),
    CONSTRAINT uq_locations_code UNIQUE (code),
    CONSTRAINT fk_locations_parent
        FOREIGN KEY (parent_id) REFERENCES dbo.locations(id),
    CONSTRAINT ck_locations_type
        CHECK (location_type IN ('CAMPUS', 'BUILDING', 'FLOOR', 'ROOM', 'AREA')),
    CONSTRAINT ck_locations_not_self_parent
        CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX idx_locations_parent_id ON dbo.locations (parent_id);
CREATE INDEX idx_locations_active_type ON dbo.locations (active, location_type);

CREATE TABLE dbo.equipment_types (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name NVARCHAR(150) NOT NULL,
    description NVARCHAR(500) NULL,
    active BIT NOT NULL
        CONSTRAINT df_equipment_types_active DEFAULT 1,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_equipment_types_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_equipment_types_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_equipment_types PRIMARY KEY (id),
    CONSTRAINT uq_equipment_types_code UNIQUE (code)
);

CREATE TABLE dbo.assets (
    id BIGINT IDENTITY(1,1) NOT NULL,
    asset_code VARCHAR(80) NOT NULL,
    serial_number NVARCHAR(120) NULL,
    equipment_type_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    asset_status VARCHAR(30) NOT NULL
        CONSTRAINT df_assets_status DEFAULT 'ACTIVE',
    description NVARCHAR(500) NULL,
    purchased_at DATE NULL,
    version BIGINT NOT NULL
        CONSTRAINT df_assets_version DEFAULT 0,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_assets_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_assets_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_assets PRIMARY KEY (id),
    CONSTRAINT uq_assets_asset_code UNIQUE (asset_code),
    CONSTRAINT fk_assets_equipment_type
        FOREIGN KEY (equipment_type_id) REFERENCES dbo.equipment_types(id),
    CONSTRAINT fk_assets_location
        FOREIGN KEY (location_id) REFERENCES dbo.locations(id),
    CONSTRAINT ck_assets_status
        CHECK (asset_status IN ('ACTIVE', 'UNDER_MAINTENANCE', 'OUT_OF_SERVICE', 'RETIRED'))
);

CREATE INDEX idx_assets_equipment_type_id ON dbo.assets (equipment_type_id);
CREATE INDEX idx_assets_location_status ON dbo.assets (location_id, asset_status);

CREATE TABLE dbo.asset_status_history (
    id BIGINT IDENTITY(1,1) NOT NULL,
    asset_id BIGINT NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    reason NVARCHAR(500) NOT NULL,
    changed_by BIGINT NULL,
    changed_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_asset_status_history_changed_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_asset_status_history PRIMARY KEY (id),
    CONSTRAINT fk_asset_status_history_asset
        FOREIGN KEY (asset_id) REFERENCES dbo.assets(id),
    CONSTRAINT fk_asset_status_history_user
        FOREIGN KEY (changed_by) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_asset_history_from_status
        CHECK (from_status IS NULL OR from_status IN ('ACTIVE', 'UNDER_MAINTENANCE', 'OUT_OF_SERVICE', 'RETIRED')),
    CONSTRAINT ck_asset_history_to_status
        CHECK (to_status IN ('ACTIVE', 'UNDER_MAINTENANCE', 'OUT_OF_SERVICE', 'RETIRED'))
);

CREATE INDEX idx_asset_status_history_asset_time
    ON dbo.asset_status_history (asset_id, changed_at DESC);


GO

/* ===== END V2__create_location_and_asset_tables.sql ===== */

/* ===== BEGIN V3__create_dynamic_category_tables.sql ===== */
CREATE TABLE dbo.incident_categories (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name NVARCHAR(150) NOT NULL,
    description NVARCHAR(1000) NULL,
    asset_policy VARCHAR(20) NOT NULL
        CONSTRAINT df_incident_categories_asset_policy DEFAULT 'OPTIONAL',
    location_required BIT NOT NULL
        CONSTRAINT df_incident_categories_location_required DEFAULT 1,
    default_priority VARCHAR(20) NOT NULL
        CONSTRAINT df_incident_categories_priority DEFAULT 'NORMAL',
    sla_hours INT NULL,
    active BIT NOT NULL
        CONSTRAINT df_incident_categories_active DEFAULT 1,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_incident_categories_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_incident_categories_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_incident_categories PRIMARY KEY (id),
    CONSTRAINT uq_incident_categories_code UNIQUE (code),
    CONSTRAINT ck_incident_categories_asset_policy
        CHECK (asset_policy IN ('REQUIRED', 'OPTIONAL', 'FORBIDDEN')),
    CONSTRAINT ck_incident_categories_priority
        CHECK (default_priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_incident_categories_sla_hours
        CHECK (sla_hours IS NULL OR sla_hours > 0)
);

CREATE INDEX idx_incident_categories_active_name
    ON dbo.incident_categories (active, name);

CREATE TABLE dbo.category_form_versions (
    id BIGINT IDENTITY(1,1) NOT NULL,
    category_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    form_status VARCHAR(20) NOT NULL
        CONSTRAINT df_category_form_versions_status DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    published_by BIGINT NULL,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_category_form_versions_created_at DEFAULT SYSUTCDATETIME(),
    published_at DATETIMEOFFSET(7) NULL,
    CONSTRAINT pk_category_form_versions PRIMARY KEY (id),
    CONSTRAINT uq_category_form_versions_number
        UNIQUE (category_id, version_number),
    CONSTRAINT uq_category_form_versions_category_id
        UNIQUE (category_id, id),
    CONSTRAINT fk_category_form_versions_category
        FOREIGN KEY (category_id) REFERENCES dbo.incident_categories(id),
    CONSTRAINT fk_category_form_versions_created_by
        FOREIGN KEY (created_by) REFERENCES dbo.app_users(id),
    CONSTRAINT fk_category_form_versions_published_by
        FOREIGN KEY (published_by) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_category_form_versions_status
        CHECK (form_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_category_form_versions_publish_metadata
        CHECK (
            (form_status = 'DRAFT' AND published_at IS NULL)
            OR (form_status IN ('PUBLISHED', 'ARCHIVED') AND published_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_category_form_versions_one_published
    ON dbo.category_form_versions (category_id)
    WHERE form_status = 'PUBLISHED';

CREATE TABLE dbo.field_definitions (
    id BIGINT IDENTITY(1,1) NOT NULL,
    form_version_id BIGINT NOT NULL,
    field_key VARCHAR(80) NOT NULL,
    label NVARCHAR(150) NOT NULL,
    field_type VARCHAR(30) NOT NULL,
    help_text NVARCHAR(500) NULL,
    placeholder NVARCHAR(250) NULL,
    required BIT NOT NULL
        CONSTRAINT df_field_definitions_required DEFAULT 0,
    display_order INT NOT NULL,
    validation_rules NVARCHAR(MAX) NOT NULL
        CONSTRAINT df_field_definitions_validation DEFAULT N'{}',
    CONSTRAINT pk_field_definitions PRIMARY KEY (id),
    CONSTRAINT uq_field_definitions_key
        UNIQUE (form_version_id, field_key),
    CONSTRAINT uq_field_definitions_form_id
        UNIQUE (form_version_id, id),
    CONSTRAINT fk_field_definitions_form_version
        FOREIGN KEY (form_version_id) REFERENCES dbo.category_form_versions(id),
    CONSTRAINT ck_field_definitions_type
        CHECK (field_type IN (
            'TEXT', 'TEXTAREA', 'NUMBER', 'SELECT', 'MULTI_SELECT',
            'DATE', 'DATETIME', 'BOOLEAN', 'IMAGE'
        )),
    CONSTRAINT ck_field_definitions_display_order
        CHECK (display_order >= 0),
    CONSTRAINT ck_field_definitions_validation_json
        CHECK (ISJSON(validation_rules, OBJECT) = 1)
);

CREATE INDEX idx_field_definitions_form_order
    ON dbo.field_definitions (form_version_id, display_order);

CREATE TABLE dbo.field_options (
    id BIGINT IDENTITY(1,1) NOT NULL,
    field_definition_id BIGINT NOT NULL,
    option_value VARCHAR(100) NOT NULL,
    option_label NVARCHAR(150) NOT NULL,
    display_order INT NOT NULL,
    active BIT NOT NULL
        CONSTRAINT df_field_options_active DEFAULT 1,
    CONSTRAINT pk_field_options PRIMARY KEY (id),
    CONSTRAINT uq_field_options_value
        UNIQUE (field_definition_id, option_value),
    CONSTRAINT fk_field_options_field_definition
        FOREIGN KEY (field_definition_id) REFERENCES dbo.field_definitions(id),
    CONSTRAINT ck_field_options_display_order
        CHECK (display_order >= 0)
);

CREATE INDEX idx_field_options_field_order
    ON dbo.field_options (field_definition_id, display_order);


GO

/* ===== END V3__create_dynamic_category_tables.sql ===== */

/* ===== BEGIN V4__create_ticket_workflow_tables.sql ===== */
CREATE TABLE dbo.tickets (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_number VARCHAR(30) NOT NULL,
    reporter_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    form_version_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    asset_id BIGINT NULL,
    duplicate_of_ticket_id BIGINT NULL,
    title NVARCHAR(200) NOT NULL,
    description NVARCHAR(MAX) NOT NULL,
    priority VARCHAR(20) NOT NULL
        CONSTRAINT df_tickets_priority DEFAULT 'NORMAL',
    ticket_status VARCHAR(30) NOT NULL
        CONSTRAINT df_tickets_status DEFAULT 'SUBMITTED',
    sla_due_at DATETIMEOFFSET(7) NULL,
    resolved_at DATETIMEOFFSET(7) NULL,
    closed_at DATETIMEOFFSET(7) NULL,
    version BIGINT NOT NULL
        CONSTRAINT df_tickets_version DEFAULT 0,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_tickets_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_tickets_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_tickets PRIMARY KEY (id),
    CONSTRAINT uq_tickets_ticket_number UNIQUE (ticket_number),
    CONSTRAINT uq_tickets_id_form_version UNIQUE (id, form_version_id),
    CONSTRAINT fk_tickets_reporter
        FOREIGN KEY (reporter_id) REFERENCES dbo.app_users(id),
    CONSTRAINT fk_tickets_category
        FOREIGN KEY (category_id) REFERENCES dbo.incident_categories(id),
    CONSTRAINT fk_tickets_category_form
        FOREIGN KEY (category_id, form_version_id)
        REFERENCES dbo.category_form_versions(category_id, id),
    CONSTRAINT fk_tickets_location
        FOREIGN KEY (location_id) REFERENCES dbo.locations(id),
    CONSTRAINT fk_tickets_asset
        FOREIGN KEY (asset_id) REFERENCES dbo.assets(id),
    CONSTRAINT fk_tickets_duplicate
        FOREIGN KEY (duplicate_of_ticket_id) REFERENCES dbo.tickets(id),
    CONSTRAINT ck_tickets_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_tickets_status
        CHECK (ticket_status IN (
            'SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS',
            'RESOLVED', 'REOPENED', 'CLOSED', 'REJECTED', 'CANCELLED'
        )),
    CONSTRAINT ck_tickets_not_self_duplicate
        CHECK (duplicate_of_ticket_id IS NULL OR duplicate_of_ticket_id <> id),
    CONSTRAINT ck_tickets_lifecycle_timestamps
        CHECK (
            (resolved_at IS NULL OR ticket_status IN ('RESOLVED', 'CLOSED'))
            AND (closed_at IS NULL OR ticket_status = 'CLOSED')
        )
);

CREATE INDEX idx_tickets_status_created
    ON dbo.tickets (ticket_status, created_at DESC);
CREATE INDEX idx_tickets_reporter_created
    ON dbo.tickets (reporter_id, created_at DESC);
CREATE INDEX idx_tickets_category_status
    ON dbo.tickets (category_id, ticket_status);
CREATE INDEX idx_tickets_location_status
    ON dbo.tickets (location_id, ticket_status);
CREATE INDEX idx_tickets_asset_status
    ON dbo.tickets (asset_id, ticket_status)
    WHERE asset_id IS NOT NULL;
CREATE INDEX idx_tickets_sla_open
    ON dbo.tickets (ticket_status, sla_due_at, priority)
    WHERE sla_due_at IS NOT NULL;

CREATE TABLE dbo.ticket_field_values (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    form_version_id BIGINT NOT NULL,
    field_definition_id BIGINT NOT NULL,
    value_json NVARCHAR(MAX) NOT NULL,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_ticket_field_values_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ticket_field_values PRIMARY KEY (id),
    CONSTRAINT uq_ticket_field_values_field UNIQUE (ticket_id, field_definition_id),
    CONSTRAINT fk_ticket_field_values_ticket_form
        FOREIGN KEY (ticket_id, form_version_id)
        REFERENCES dbo.tickets(id, form_version_id),
    CONSTRAINT fk_ticket_field_values_definition_form
        FOREIGN KEY (form_version_id, field_definition_id)
        REFERENCES dbo.field_definitions(form_version_id, id),
    CONSTRAINT ck_ticket_field_values_json
        CHECK (ISJSON(value_json, VALUE) = 1)
);

CREATE INDEX idx_ticket_field_values_ticket
    ON dbo.ticket_field_values (ticket_id);

CREATE TABLE dbo.ticket_assignments (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    technician_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    assigned_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_ticket_assignments_assigned_at DEFAULT SYSUTCDATETIME(),
    accepted_at DATETIMEOFFSET(7) NULL,
    ended_at DATETIMEOFFSET(7) NULL,
    end_reason NVARCHAR(500) NULL,
    CONSTRAINT pk_ticket_assignments PRIMARY KEY (id),
    CONSTRAINT fk_ticket_assignments_ticket
        FOREIGN KEY (ticket_id) REFERENCES dbo.tickets(id),
    CONSTRAINT fk_ticket_assignments_technician
        FOREIGN KEY (technician_id) REFERENCES dbo.app_users(id),
    CONSTRAINT fk_ticket_assignments_assigned_by
        FOREIGN KEY (assigned_by) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_ticket_assignments_times
        CHECK (
            (accepted_at IS NULL OR accepted_at >= assigned_at)
            AND (ended_at IS NULL OR ended_at >= assigned_at)
        )
);

CREATE UNIQUE INDEX uq_ticket_assignments_one_active
    ON dbo.ticket_assignments (ticket_id)
    WHERE ended_at IS NULL;
CREATE INDEX idx_ticket_assignments_technician_active
    ON dbo.ticket_assignments (technician_id, ended_at, assigned_at DESC);

CREATE TABLE dbo.ticket_status_history (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    transition_action VARCHAR(40) NOT NULL,
    reason NVARCHAR(1000) NULL,
    changed_by BIGINT NOT NULL,
    changed_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_ticket_status_history_changed_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ticket_status_history PRIMARY KEY (id),
    CONSTRAINT fk_ticket_status_history_ticket
        FOREIGN KEY (ticket_id) REFERENCES dbo.tickets(id),
    CONSTRAINT fk_ticket_status_history_user
        FOREIGN KEY (changed_by) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_ticket_status_history_from
        CHECK (from_status IS NULL OR from_status IN (
            'SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS',
            'RESOLVED', 'REOPENED', 'CLOSED', 'REJECTED', 'CANCELLED'
        )),
    CONSTRAINT ck_ticket_status_history_to
        CHECK (to_status IN (
            'SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS',
            'RESOLVED', 'REOPENED', 'CLOSED', 'REJECTED', 'CANCELLED'
        ))
);

CREATE INDEX idx_ticket_status_history_ticket_time
    ON dbo.ticket_status_history (ticket_id, changed_at DESC);

CREATE TABLE dbo.work_logs (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    technician_id BIGINT NOT NULL,
    work_description NVARCHAR(MAX) NOT NULL,
    started_at DATETIMEOFFSET(7) NOT NULL,
    ended_at DATETIMEOFFSET(7) NULL,
    minutes_spent INT NULL,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_work_logs_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_work_logs PRIMARY KEY (id),
    CONSTRAINT fk_work_logs_ticket
        FOREIGN KEY (ticket_id) REFERENCES dbo.tickets(id),
    CONSTRAINT fk_work_logs_technician
        FOREIGN KEY (technician_id) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_work_logs_times
        CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT ck_work_logs_minutes
        CHECK (minutes_spent IS NULL OR minutes_spent > 0)
);

CREATE INDEX idx_work_logs_ticket_time
    ON dbo.work_logs (ticket_id, created_at DESC);

CREATE TABLE dbo.ticket_attachments (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    field_definition_id BIGINT NULL,
    uploaded_by BIGINT NOT NULL,
    attachment_type VARCHAR(30) NOT NULL,
    storage_key NVARCHAR(500) NOT NULL,
    original_name NVARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_ticket_attachments_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ticket_attachments PRIMARY KEY (id),
    CONSTRAINT uq_ticket_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_ticket_attachments_ticket
        FOREIGN KEY (ticket_id) REFERENCES dbo.tickets(id),
    CONSTRAINT fk_ticket_attachments_field
        FOREIGN KEY (field_definition_id) REFERENCES dbo.field_definitions(id),
    CONSTRAINT fk_ticket_attachments_user
        FOREIGN KEY (uploaded_by) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_ticket_attachments_type
        CHECK (attachment_type IN ('REPORT_EVIDENCE', 'WORK_RESULT', 'COMMENT')),
    CONSTRAINT ck_ticket_attachments_size
        CHECK (size_bytes > 0)
);

CREATE INDEX idx_ticket_attachments_ticket_time
    ON dbo.ticket_attachments (ticket_id, created_at DESC);

CREATE TABLE dbo.ticket_comments (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    body NVARCHAR(2000) NOT NULL,
    visibility VARCHAR(20) NOT NULL
        CONSTRAINT df_ticket_comments_visibility DEFAULT 'PUBLIC',
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_ticket_comments_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ticket_comments PRIMARY KEY (id),
    CONSTRAINT fk_ticket_comments_ticket
        FOREIGN KEY (ticket_id) REFERENCES dbo.tickets(id),
    CONSTRAINT fk_ticket_comments_author
        FOREIGN KEY (author_id) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_ticket_comments_visibility
        CHECK (visibility IN ('PUBLIC', 'INTERNAL'))
);

CREATE INDEX idx_ticket_comments_ticket_time
    ON dbo.ticket_comments (ticket_id, created_at ASC);

CREATE TABLE dbo.ticket_feedback (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    comment NVARCHAR(1000) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_ticket_feedback_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ticket_feedback PRIMARY KEY (id),
    CONSTRAINT uq_ticket_feedback_ticket UNIQUE (ticket_id),
    CONSTRAINT fk_ticket_feedback_ticket
        FOREIGN KEY (ticket_id) REFERENCES dbo.tickets(id),
    CONSTRAINT fk_ticket_feedback_reporter
        FOREIGN KEY (reporter_id) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_ticket_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE dbo.ticket_accuracy_reviews (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    review_result VARCHAR(40) NOT NULL,
    reason NVARCHAR(1000) NOT NULL,
    reviewed_by BIGINT NOT NULL,
    reviewed_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_ticket_accuracy_reviews_reviewed_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ticket_accuracy_reviews PRIMARY KEY (id),
    CONSTRAINT fk_ticket_accuracy_reviews_ticket
        FOREIGN KEY (ticket_id) REFERENCES dbo.tickets(id),
    CONSTRAINT fk_ticket_accuracy_reviews_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_ticket_accuracy_reviews_result
        CHECK (review_result IN (
            'ACCURATE', 'INACCURATE', 'INSUFFICIENT_EVIDENCE', 'NO_FAULT_FOUND'
        ))
);

CREATE INDEX idx_ticket_accuracy_reviews_ticket_time
    ON dbo.ticket_accuracy_reviews (ticket_id, reviewed_at DESC);

CREATE TABLE dbo.notifications (
    id BIGINT IDENTITY(1,1) NOT NULL,
    recipient_id BIGINT NOT NULL,
    ticket_id BIGINT NULL,
    notification_type VARCHAR(40) NOT NULL,
    title NVARCHAR(200) NOT NULL,
    message NVARCHAR(1000) NOT NULL,
    read_at DATETIMEOFFSET(7) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_notifications_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_id) REFERENCES dbo.app_users(id),
    CONSTRAINT fk_notifications_ticket
        FOREIGN KEY (ticket_id) REFERENCES dbo.tickets(id)
);

CREATE INDEX idx_notifications_recipient_unread
    ON dbo.notifications (recipient_id, created_at DESC)
    WHERE read_at IS NULL;


GO

/* ===== END V4__create_ticket_workflow_tables.sql ===== */

/* ===== BEGIN V5__seed_reference_data.sql ===== */
IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE code = 'REQUESTER')
    INSERT INTO dbo.roles (code, name) VALUES ('REQUESTER', N'Người báo cáo');
IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE code = 'TECHNICIAN')
    INSERT INTO dbo.roles (code, name) VALUES ('TECHNICIAN', N'Kỹ thuật viên');
IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE code = 'MANAGER')
    INSERT INTO dbo.roles (code, name) VALUES ('MANAGER', N'Quản lý vận hành');
IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE code = 'ADMIN')
    INSERT INTO dbo.roles (code, name) VALUES ('ADMIN', N'Quản trị viên');

IF NOT EXISTS (SELECT 1 FROM dbo.locations WHERE code = 'CAMPUS-MAIN')
    INSERT INTO dbo.locations (code, name, location_type, description)
    VALUES ('CAMPUS-MAIN', N'Campus chính', 'CAMPUS', N'Dữ liệu mẫu cho môi trường phát triển');

DECLARE @campus_id BIGINT = (SELECT id FROM dbo.locations WHERE code = 'CAMPUS-MAIN');

IF NOT EXISTS (SELECT 1 FROM dbo.locations WHERE code = 'BLD-ALPHA')
    INSERT INTO dbo.locations (code, name, location_type, parent_id)
    VALUES ('BLD-ALPHA', N'Tòa Alpha', 'BUILDING', @campus_id);

DECLARE @building_id BIGINT = (SELECT id FROM dbo.locations WHERE code = 'BLD-ALPHA');

IF NOT EXISTS (SELECT 1 FROM dbo.locations WHERE code = 'FL-ALPHA-1')
    INSERT INTO dbo.locations (code, name, location_type, parent_id)
    VALUES ('FL-ALPHA-1', N'Tầng 1 - Alpha', 'FLOOR', @building_id);

DECLARE @floor_id BIGINT = (SELECT id FROM dbo.locations WHERE code = 'FL-ALPHA-1');

IF NOT EXISTS (SELECT 1 FROM dbo.locations WHERE code = 'ROOM-A101')
    INSERT INTO dbo.locations (code, name, location_type, parent_id)
    VALUES ('ROOM-A101', N'Phòng A101', 'ROOM', @floor_id);
IF NOT EXISTS (SELECT 1 FROM dbo.locations WHERE code = 'ROOM-A102')
    INSERT INTO dbo.locations (code, name, location_type, parent_id)
    VALUES ('ROOM-A102', N'Phòng A102', 'ROOM', @floor_id);

IF NOT EXISTS (SELECT 1 FROM dbo.equipment_types WHERE code = 'PROJECTOR')
    INSERT INTO dbo.equipment_types (code, name, description)
    VALUES ('PROJECTOR', N'Máy chiếu', N'Máy chiếu trong phòng học hoặc phòng họp');
IF NOT EXISTS (SELECT 1 FROM dbo.equipment_types WHERE code = 'AIR_CONDITIONER')
    INSERT INTO dbo.equipment_types (code, name, description)
    VALUES ('AIR_CONDITIONER', N'Điều hòa', N'Thiết bị điều hòa không khí');

DECLARE @room_a101 BIGINT = (SELECT id FROM dbo.locations WHERE code = 'ROOM-A101');
DECLARE @room_a102 BIGINT = (SELECT id FROM dbo.locations WHERE code = 'ROOM-A102');
DECLARE @projector_type BIGINT = (SELECT id FROM dbo.equipment_types WHERE code = 'PROJECTOR');
DECLARE @aircon_type BIGINT = (SELECT id FROM dbo.equipment_types WHERE code = 'AIR_CONDITIONER');

IF NOT EXISTS (SELECT 1 FROM dbo.assets WHERE asset_code = 'PRJ-A101-01')
    INSERT INTO dbo.assets (asset_code, serial_number, equipment_type_id, location_id, description)
    VALUES ('PRJ-A101-01', N'PJ-DEMO-0001', @projector_type, @room_a101, N'Máy chiếu mẫu tại A101');
IF NOT EXISTS (SELECT 1 FROM dbo.assets WHERE asset_code = 'AC-A102-01')
    INSERT INTO dbo.assets (asset_code, serial_number, equipment_type_id, location_id, description)
    VALUES ('AC-A102-01', N'AC-DEMO-0001', @aircon_type, @room_a102, N'Điều hòa mẫu tại A102');

IF NOT EXISTS (SELECT 1 FROM dbo.incident_categories WHERE code = 'PROJECTOR_ISSUE')
    INSERT INTO dbo.incident_categories
        (code, name, description, asset_policy, default_priority, sla_hours)
    VALUES
        ('PROJECTOR_ISSUE', N'Sự cố máy chiếu', N'Lỗi hiển thị, tín hiệu hoặc đèn báo', 'REQUIRED', 'NORMAL', 24);
IF NOT EXISTS (SELECT 1 FROM dbo.incident_categories WHERE code = 'AIR_CONDITIONER_ISSUE')
    INSERT INTO dbo.incident_categories
        (code, name, description, asset_policy, default_priority, sla_hours)
    VALUES
        ('AIR_CONDITIONER_ISSUE', N'Sự cố điều hòa', N'Điều hòa không mát, chảy nước hoặc có tiếng ồn', 'OPTIONAL', 'NORMAL', 24);
IF NOT EXISTS (SELECT 1 FROM dbo.incident_categories WHERE code = 'WATER_LEAK')
    INSERT INTO dbo.incident_categories
        (code, name, description, asset_policy, default_priority, sla_hours)
    VALUES
        ('WATER_LEAK', N'Rò rỉ nước', N'Rò nước tại phòng học hoặc khu vực chung', 'FORBIDDEN', 'HIGH', 4);
IF NOT EXISTS (SELECT 1 FROM dbo.incident_categories WHERE code = 'SAFETY_INCIDENT')
    INSERT INTO dbo.incident_categories
        (code, name, description, asset_policy, default_priority, sla_hours)
    VALUES
        ('SAFETY_INCIDENT', N'Sự cố an toàn', N'Nguy cơ ảnh hưởng đến an toàn con người hoặc tài sản', 'OPTIONAL', 'URGENT', 1);

DECLARE @projector_category BIGINT = (SELECT id FROM dbo.incident_categories WHERE code = 'PROJECTOR_ISSUE');
DECLARE @aircon_category BIGINT = (SELECT id FROM dbo.incident_categories WHERE code = 'AIR_CONDITIONER_ISSUE');
DECLARE @water_category BIGINT = (SELECT id FROM dbo.incident_categories WHERE code = 'WATER_LEAK');
DECLARE @safety_category BIGINT = (SELECT id FROM dbo.incident_categories WHERE code = 'SAFETY_INCIDENT');

IF NOT EXISTS (SELECT 1 FROM dbo.category_form_versions WHERE category_id = @projector_category AND version_number = 1)
    INSERT INTO dbo.category_form_versions (category_id, version_number, form_status, published_at)
    VALUES (@projector_category, 1, 'PUBLISHED', SYSUTCDATETIME());
IF NOT EXISTS (SELECT 1 FROM dbo.category_form_versions WHERE category_id = @aircon_category AND version_number = 1)
    INSERT INTO dbo.category_form_versions (category_id, version_number, form_status, published_at)
    VALUES (@aircon_category, 1, 'PUBLISHED', SYSUTCDATETIME());
IF NOT EXISTS (SELECT 1 FROM dbo.category_form_versions WHERE category_id = @water_category AND version_number = 1)
    INSERT INTO dbo.category_form_versions (category_id, version_number, form_status, published_at)
    VALUES (@water_category, 1, 'PUBLISHED', SYSUTCDATETIME());
IF NOT EXISTS (SELECT 1 FROM dbo.category_form_versions WHERE category_id = @safety_category AND version_number = 1)
    INSERT INTO dbo.category_form_versions (category_id, version_number, form_status, published_at)
    VALUES (@safety_category, 1, 'PUBLISHED', SYSUTCDATETIME());

DECLARE @projector_form BIGINT = (
    SELECT id FROM dbo.category_form_versions WHERE category_id = @projector_category AND version_number = 1
);
DECLARE @aircon_form BIGINT = (
    SELECT id FROM dbo.category_form_versions WHERE category_id = @aircon_category AND version_number = 1
);
DECLARE @water_form BIGINT = (
    SELECT id FROM dbo.category_form_versions WHERE category_id = @water_category AND version_number = 1
);
DECLARE @safety_form BIGINT = (
    SELECT id FROM dbo.category_form_versions WHERE category_id = @safety_category AND version_number = 1
);

IF NOT EXISTS (SELECT 1 FROM dbo.field_definitions WHERE form_version_id = @projector_form AND field_key = 'screen_image')
    INSERT INTO dbo.field_definitions
        (form_version_id, field_key, label, field_type, help_text, required, display_order, validation_rules)
    VALUES
        (@projector_form, 'screen_image', N'Ảnh màn hình hoặc hình ảnh lỗi', 'IMAGE',
         N'Chụp rõ màn hình và đèn báo nếu có', 1, 1,
         N'{"minFiles":1,"maxFiles":3,"allowedTypes":["image/jpeg","image/png"]}');
IF NOT EXISTS (SELECT 1 FROM dbo.field_definitions WHERE form_version_id = @projector_form AND field_key = 'asset_code')
    INSERT INTO dbo.field_definitions
        (form_version_id, field_key, label, field_type, placeholder, required, display_order, validation_rules)
    VALUES
        (@projector_form, 'asset_code', N'Mã thiết bị', 'TEXT', N'Ví dụ: PRJ-A101-01', 1, 2,
         N'{"minLength":3,"maxLength":80}');
IF NOT EXISTS (SELECT 1 FROM dbo.field_definitions WHERE form_version_id = @projector_form AND field_key = 'indicator_description')
    INSERT INTO dbo.field_definitions
        (form_version_id, field_key, label, field_type, required, display_order, validation_rules)
    VALUES
        (@projector_form, 'indicator_description', N'Mô tả đèn báo và hiện tượng', 'TEXTAREA', 1, 3,
         N'{"minLength":10,"maxLength":500}');

IF NOT EXISTS (SELECT 1 FROM dbo.field_definitions WHERE form_version_id = @aircon_form AND field_key = 'room_number')
    INSERT INTO dbo.field_definitions
        (form_version_id, field_key, label, field_type, required, display_order, validation_rules)
    VALUES
        (@aircon_form, 'room_number', N'Số phòng', 'TEXT', 1, 1, N'{"maxLength":30}');
IF NOT EXISTS (SELECT 1 FROM dbo.field_definitions WHERE form_version_id = @aircon_form AND field_key = 'symptom')
    INSERT INTO dbo.field_definitions
        (form_version_id, field_key, label, field_type, required, display_order, validation_rules)
    VALUES
        (@aircon_form, 'symptom', N'Hiện tượng', 'SELECT', 1, 2, N'{}');
IF NOT EXISTS (SELECT 1 FROM dbo.field_definitions WHERE form_version_id = @aircon_form AND field_key = 'occurred_at')
    INSERT INTO dbo.field_definitions
        (form_version_id, field_key, label, field_type, required, display_order, validation_rules)
    VALUES
        (@aircon_form, 'occurred_at', N'Thời điểm xảy ra', 'DATETIME', 1, 3, N'{"notInFuture":true}');

DECLARE @symptom_field BIGINT = (
    SELECT id FROM dbo.field_definitions WHERE form_version_id = @aircon_form AND field_key = 'symptom'
);
IF NOT EXISTS (SELECT 1 FROM dbo.field_options WHERE field_definition_id = @symptom_field AND option_value = 'NOT_COOLING')
    INSERT INTO dbo.field_options (field_definition_id, option_value, option_label, display_order)
    VALUES (@symptom_field, 'NOT_COOLING', N'Không mát', 1);
IF NOT EXISTS (SELECT 1 FROM dbo.field_options WHERE field_definition_id = @symptom_field AND option_value = 'WATER_LEAK')
    INSERT INTO dbo.field_options (field_definition_id, option_value, option_label, display_order)
    VALUES (@symptom_field, 'WATER_LEAK', N'Chảy nước', 2);
IF NOT EXISTS (SELECT 1 FROM dbo.field_options WHERE field_definition_id = @symptom_field AND option_value = 'NOISE')
    INSERT INTO dbo.field_options (field_definition_id, option_value, option_label, display_order)
    VALUES (@symptom_field, 'NOISE', N'Tiếng ồn bất thường', 3);

IF NOT EXISTS (SELECT 1 FROM dbo.field_definitions WHERE form_version_id = @water_form AND field_key = 'leak_image')
    INSERT INTO dbo.field_definitions
        (form_version_id, field_key, label, field_type, required, display_order, validation_rules)
    VALUES
        (@water_form, 'leak_image', N'Ảnh vị trí rò nước', 'IMAGE', 1, 1,
         N'{"minFiles":1,"maxFiles":5,"allowedTypes":["image/jpeg","image/png"]}');
IF NOT EXISTS (SELECT 1 FROM dbo.field_definitions WHERE form_version_id = @water_form AND field_key = 'precise_location')
    INSERT INTO dbo.field_definitions
        (form_version_id, field_key, label, field_type, required, display_order, validation_rules)
    VALUES
        (@water_form, 'precise_location', N'Vị trí cụ thể', 'TEXTAREA', 1, 2,
         N'{"minLength":10,"maxLength":500}');

IF NOT EXISTS (SELECT 1 FROM dbo.field_definitions WHERE form_version_id = @safety_form AND field_key = 'impact_scope')
    INSERT INTO dbo.field_definitions
        (form_version_id, field_key, label, field_type, help_text, required, display_order, validation_rules)
    VALUES
        (@safety_form, 'impact_scope', N'Phạm vi ảnh hưởng', 'TEXTAREA',
         N'Nêu khu vực, số người hoặc tài sản có thể bị ảnh hưởng', 1, 1,
         N'{"minLength":20,"maxLength":1000}');


GO

/* ===== END V5__seed_reference_data.sql ===== */

/* ===== BEGIN V6__complete_erd_support_tables.sql ===== */
-- Complete the physical SQL Server schema for the workforce, SLA, audit,
-- and reviewed false-report areas shown in the Nexora ERD.
-- Existing V1-V5 migrations remain immutable; this migration adds the
-- missing structures and enriches existing tables without dropping data.

CREATE TABLE dbo.technician_profiles (
    technician_id BIGINT NOT NULL,
    max_active_tickets INT NOT NULL
        CONSTRAINT df_technician_profiles_max_active DEFAULT 5,
    available BIT NOT NULL
        CONSTRAINT df_technician_profiles_available DEFAULT 1,
    version BIGINT NOT NULL
        CONSTRAINT df_technician_profiles_version DEFAULT 0,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_technician_profiles_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_technician_profiles_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_technician_profiles PRIMARY KEY (technician_id),
    CONSTRAINT fk_technician_profiles_user
        FOREIGN KEY (technician_id) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_technician_profiles_max_active
        CHECK (max_active_tickets > 0)
);

CREATE TABLE dbo.skills (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name NVARCHAR(150) NOT NULL,
    active BIT NOT NULL
        CONSTRAINT df_skills_active DEFAULT 1,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_skills_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_skills_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_skills PRIMARY KEY (id),
    CONSTRAINT uq_skills_code UNIQUE (code)
);

CREATE TABLE dbo.technician_skills (
    technician_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    proficiency_level TINYINT NOT NULL,
    assigned_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_technician_skills_assigned_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_technician_skills PRIMARY KEY (technician_id, skill_id),
    CONSTRAINT fk_technician_skills_technician
        FOREIGN KEY (technician_id) REFERENCES dbo.technician_profiles(technician_id),
    CONSTRAINT fk_technician_skills_skill
        FOREIGN KEY (skill_id) REFERENCES dbo.skills(id),
    CONSTRAINT ck_technician_skills_proficiency
        CHECK (proficiency_level BETWEEN 1 AND 5)
);

CREATE INDEX idx_technician_skills_skill
    ON dbo.technician_skills (skill_id, proficiency_level DESC);

CREATE TABLE dbo.technician_service_areas (
    technician_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    CONSTRAINT pk_technician_service_areas PRIMARY KEY (technician_id, location_id),
    CONSTRAINT fk_technician_service_areas_technician
        FOREIGN KEY (technician_id) REFERENCES dbo.technician_profiles(technician_id),
    CONSTRAINT fk_technician_service_areas_location
        FOREIGN KEY (location_id) REFERENCES dbo.locations(id)
);

CREATE INDEX idx_technician_service_areas_location
    ON dbo.technician_service_areas (location_id, technician_id);

CREATE TABLE dbo.sla_policies (
    id BIGINT IDENTITY(1,1) NOT NULL,
    policy_name NVARCHAR(150) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    resolution_minutes INT NOT NULL,
    pause_waiting_requester BIT NOT NULL
        CONSTRAINT df_sla_policies_pause_requester DEFAULT 0,
    active BIT NOT NULL
        CONSTRAINT df_sla_policies_active DEFAULT 1,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_sla_policies_created_at DEFAULT SYSUTCDATETIME(),
    updated_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_sla_policies_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_sla_policies PRIMARY KEY (id),
    CONSTRAINT uq_sla_policies_name UNIQUE (policy_name),
    CONSTRAINT ck_sla_policies_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_sla_policies_resolution_minutes
        CHECK (resolution_minutes > 0)
);

ALTER TABLE dbo.user_roles
ADD assigned_by BIGINT NULL;
GO

ALTER TABLE dbo.user_roles
ADD CONSTRAINT fk_user_roles_assigned_by
    FOREIGN KEY (assigned_by) REFERENCES dbo.app_users(id);

ALTER TABLE dbo.equipment_types
ADD brand NVARCHAR(100) NULL,
    model NVARCHAR(100) NULL;

ALTER TABLE dbo.assets
ADD warranty_until DATE NULL;

IF EXISTS (
    SELECT serial_number
    FROM dbo.assets
    WHERE serial_number IS NOT NULL
    GROUP BY serial_number
    HAVING COUNT(*) > 1
)
    THROW 51001, 'Cannot enforce unique asset serial numbers. Resolve duplicate serial_number values before applying V6.', 1;

CREATE UNIQUE INDEX uq_assets_serial_number
    ON dbo.assets (serial_number)
    WHERE serial_number IS NOT NULL;

ALTER TABLE dbo.asset_status_history
ADD ticket_id BIGINT NULL;
GO

ALTER TABLE dbo.asset_status_history
ADD CONSTRAINT fk_asset_status_history_ticket
    FOREIGN KEY (ticket_id) REFERENCES dbo.tickets(id);

CREATE INDEX idx_asset_status_history_ticket
    ON dbo.asset_status_history (ticket_id, changed_at DESC)
    WHERE ticket_id IS NOT NULL;

ALTER TABLE dbo.incident_categories
ADD created_by BIGINT NULL;
GO

ALTER TABLE dbo.incident_categories
ADD CONSTRAINT fk_incident_categories_created_by
    FOREIGN KEY (created_by) REFERENCES dbo.app_users(id);

ALTER TABLE dbo.category_form_versions
ADD requires_equipment BIT NOT NULL
        CONSTRAINT df_category_form_versions_requires_equipment DEFAULT 0,
    min_attachment_count INT NOT NULL
        CONSTRAINT df_category_form_versions_min_attachments DEFAULT 0,
    default_skill_id BIGINT NULL,
    default_sla_policy_id BIGINT NULL;
GO

ALTER TABLE dbo.category_form_versions
ADD CONSTRAINT ck_category_form_versions_min_attachments
        CHECK (min_attachment_count >= 0),
    CONSTRAINT fk_category_form_versions_default_skill
        FOREIGN KEY (default_skill_id) REFERENCES dbo.skills(id),
    CONSTRAINT fk_category_form_versions_default_sla
        FOREIGN KEY (default_sla_policy_id) REFERENCES dbo.sla_policies(id);

CREATE INDEX idx_category_form_versions_default_skill
    ON dbo.category_form_versions (default_skill_id)
    WHERE default_skill_id IS NOT NULL;

ALTER TABLE dbo.tickets
ADD required_skill_id BIGINT NULL,
    resolution_outcome VARCHAR(30) NULL;
GO

ALTER TABLE dbo.tickets
ADD CONSTRAINT fk_tickets_required_skill
        FOREIGN KEY (required_skill_id) REFERENCES dbo.skills(id),
    CONSTRAINT ck_tickets_resolution_outcome
        CHECK (
            resolution_outcome IS NULL
            OR (
                resolution_outcome IN (
                    'FIXED', 'TEMPORARY_FIX', 'NO_FAULT_FOUND', 'DUPLICATE', 'UNRESOLVED'
                )
                AND ticket_status IN ('RESOLVED', 'CLOSED')
            )
        );

CREATE INDEX idx_tickets_required_skill_status
    ON dbo.tickets (required_skill_id, ticket_status)
    WHERE required_skill_id IS NOT NULL;

ALTER TABLE dbo.ticket_assignments
ADD override_reason NVARCHAR(1000) NULL;

ALTER TABLE dbo.ticket_assignments
ADD CONSTRAINT ck_ticket_assignments_accept_before_end
    CHECK (accepted_at IS NULL OR ended_at IS NULL OR accepted_at <= ended_at);

ALTER TABLE dbo.work_logs
ADD log_type VARCHAR(30) NOT NULL
    CONSTRAINT df_work_logs_type DEFAULT 'PROGRESS';
GO

ALTER TABLE dbo.work_logs
ADD CONSTRAINT ck_work_logs_type
    CHECK (log_type IN (
        'DIAGNOSIS', 'PROGRESS', 'REPAIR', 'RESOLUTION', 'NO_FAULT_FOUND'
    ));

ALTER TABLE dbo.ticket_comments
ADD edited_at DATETIMEOFFSET(7) NULL;
GO

ALTER TABLE dbo.ticket_comments
ADD CONSTRAINT ck_ticket_comments_edited_at
    CHECK (edited_at IS NULL OR edited_at >= created_at);

ALTER TABLE dbo.ticket_attachments
ADD form_version_id BIGINT NULL;
GO

UPDATE attachment
SET attachment.form_version_id = ticket.form_version_id
FROM dbo.ticket_attachments AS attachment
JOIN dbo.tickets AS ticket ON ticket.id = attachment.ticket_id;

ALTER TABLE dbo.ticket_attachments
ALTER COLUMN form_version_id BIGINT NOT NULL;

ALTER TABLE dbo.ticket_attachments
ADD CONSTRAINT fk_ticket_attachments_ticket_form
        FOREIGN KEY (ticket_id, form_version_id)
        REFERENCES dbo.tickets(id, form_version_id),
    CONSTRAINT fk_ticket_attachments_definition_form
        FOREIGN KEY (form_version_id, field_definition_id)
        REFERENCES dbo.field_definitions(form_version_id, id);

-- Backfill a profile for any technician already referenced before changing
-- these foreign keys to the ERD-authoritative TECHNICIAN_PROFILE target.
INSERT INTO dbo.technician_profiles (technician_id)
SELECT referenced.technician_id
FROM (
    SELECT technician_id FROM dbo.ticket_assignments
    UNION
    SELECT technician_id FROM dbo.work_logs
) AS referenced
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.technician_profiles profile
    WHERE profile.technician_id = referenced.technician_id
);

ALTER TABLE dbo.ticket_assignments
DROP CONSTRAINT fk_ticket_assignments_technician;
GO

ALTER TABLE dbo.ticket_assignments
ADD CONSTRAINT fk_ticket_assignments_technician
    FOREIGN KEY (technician_id)
    REFERENCES dbo.technician_profiles(technician_id);

ALTER TABLE dbo.work_logs
DROP CONSTRAINT fk_work_logs_technician;
GO

ALTER TABLE dbo.work_logs
ADD CONSTRAINT fk_work_logs_technician
    FOREIGN KEY (technician_id)
    REFERENCES dbo.technician_profiles(technician_id);

CREATE TABLE dbo.audit_logs (
    id BIGINT IDENTITY(1,1) NOT NULL,
    actor_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id BIGINT NOT NULL,
    before_data NVARCHAR(MAX) NULL,
    after_data NVARCHAR(MAX) NULL,
    reason NVARCHAR(1000) NULL,
    created_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_audit_logs_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_actor
        FOREIGN KEY (actor_id) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_audit_logs_before_json
        CHECK (before_data IS NULL OR ISJSON(before_data) = 1),
    CONSTRAINT ck_audit_logs_after_json
        CHECK (after_data IS NULL OR ISJSON(after_data) = 1)
);

CREATE INDEX idx_audit_logs_target_time
    ON dbo.audit_logs (target_type, target_id, created_at DESC);
CREATE INDEX idx_audit_logs_actor_time
    ON dbo.audit_logs (actor_id, created_at DESC)
    WHERE actor_id IS NOT NULL;

-- P1 tables: NO_FAULT_FOUND never creates a violation automatically.
-- A manager must explicitly open and review a case with evidence.
CREATE TABLE dbo.violation_cases (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_id BIGINT NOT NULL,
    subject_user_id BIGINT NOT NULL,
    opened_by BIGINT NOT NULL,
    review_status VARCHAR(30) NOT NULL
        CONSTRAINT df_violation_cases_status DEFAULT 'OPEN',
    allegation_reason NVARCHAR(2000) NOT NULL,
    decision_reason NVARCHAR(2000) NULL,
    reviewed_by BIGINT NULL,
    restriction_until DATETIMEOFFSET(7) NULL,
    opened_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_violation_cases_opened_at DEFAULT SYSUTCDATETIME(),
    reviewed_at DATETIMEOFFSET(7) NULL,
    CONSTRAINT pk_violation_cases PRIMARY KEY (id),
    CONSTRAINT uq_violation_cases_ticket UNIQUE (ticket_id),
    CONSTRAINT fk_violation_cases_ticket
        FOREIGN KEY (ticket_id) REFERENCES dbo.tickets(id),
    CONSTRAINT fk_violation_cases_subject
        FOREIGN KEY (subject_user_id) REFERENCES dbo.app_users(id),
    CONSTRAINT fk_violation_cases_opened_by
        FOREIGN KEY (opened_by) REFERENCES dbo.app_users(id),
    CONSTRAINT fk_violation_cases_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_violation_cases_status
        CHECK (review_status IN (
            'OPEN', 'UNDER_REVIEW', 'CONFIRMED', 'DISMISSED', 'RESTRICTED'
        )),
    CONSTRAINT ck_violation_cases_review_metadata
        CHECK (
            (review_status IN ('OPEN', 'UNDER_REVIEW') AND reviewed_at IS NULL)
            OR (review_status IN ('CONFIRMED', 'DISMISSED', 'RESTRICTED')
                AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
                AND decision_reason IS NOT NULL)
        )
);

CREATE INDEX idx_violation_cases_subject_status
    ON dbo.violation_cases (subject_user_id, review_status, opened_at DESC);

CREATE TABLE dbo.violation_appeals (
    id BIGINT IDENTITY(1,1) NOT NULL,
    violation_case_id BIGINT NOT NULL,
    submitted_by BIGINT NOT NULL,
    appeal_text NVARCHAR(2000) NOT NULL,
    appeal_status VARCHAR(30) NOT NULL
        CONSTRAINT df_violation_appeals_status DEFAULT 'SUBMITTED',
    reviewed_by BIGINT NULL,
    decision_note NVARCHAR(2000) NULL,
    submitted_at DATETIMEOFFSET(7) NOT NULL
        CONSTRAINT df_violation_appeals_submitted_at DEFAULT SYSUTCDATETIME(),
    reviewed_at DATETIMEOFFSET(7) NULL,
    CONSTRAINT pk_violation_appeals PRIMARY KEY (id),
    CONSTRAINT fk_violation_appeals_case
        FOREIGN KEY (violation_case_id) REFERENCES dbo.violation_cases(id),
    CONSTRAINT fk_violation_appeals_submitted_by
        FOREIGN KEY (submitted_by) REFERENCES dbo.app_users(id),
    CONSTRAINT fk_violation_appeals_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES dbo.app_users(id),
    CONSTRAINT ck_violation_appeals_status
        CHECK (appeal_status IN (
            'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN'
        )),
    CONSTRAINT ck_violation_appeals_review_metadata
        CHECK (
            (appeal_status IN ('SUBMITTED', 'UNDER_REVIEW', 'WITHDRAWN')
                AND reviewed_at IS NULL)
            OR (appeal_status IN ('APPROVED', 'REJECTED')
                AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
                AND decision_note IS NOT NULL)
        )
);

CREATE INDEX idx_violation_appeals_case_time
    ON dbo.violation_appeals (violation_case_id, submitted_at DESC);


GO

/* ===== END V6__complete_erd_support_tables.sql ===== */

/* ===== BEGIN V7__seed_skill_and_sla_reference_data.sql ===== */
IF NOT EXISTS (SELECT 1 FROM dbo.skills WHERE code = 'PROJECTOR')
    INSERT INTO dbo.skills (code, name) VALUES ('PROJECTOR', N'Sửa chữa máy chiếu');
IF NOT EXISTS (SELECT 1 FROM dbo.skills WHERE code = 'HVAC')
    INSERT INTO dbo.skills (code, name) VALUES ('HVAC', N'Điều hòa và thông gió');
IF NOT EXISTS (SELECT 1 FROM dbo.skills WHERE code = 'PLUMBING')
    INSERT INTO dbo.skills (code, name) VALUES ('PLUMBING', N'Cấp thoát nước');
IF NOT EXISTS (SELECT 1 FROM dbo.skills WHERE code = 'SAFETY')
    INSERT INTO dbo.skills (code, name) VALUES ('SAFETY', N'An toàn cơ sở vật chất');

IF NOT EXISTS (SELECT 1 FROM dbo.sla_policies WHERE policy_name = N'Low - 72 hours')
    INSERT INTO dbo.sla_policies
        (policy_name, priority, resolution_minutes, pause_waiting_requester)
    VALUES (N'Low - 72 hours', 'LOW', 4320, 1);
IF NOT EXISTS (SELECT 1 FROM dbo.sla_policies WHERE policy_name = N'Normal - 24 hours')
    INSERT INTO dbo.sla_policies
        (policy_name, priority, resolution_minutes, pause_waiting_requester)
    VALUES (N'Normal - 24 hours', 'NORMAL', 1440, 1);
IF NOT EXISTS (SELECT 1 FROM dbo.sla_policies WHERE policy_name = N'High - 4 hours')
    INSERT INTO dbo.sla_policies
        (policy_name, priority, resolution_minutes, pause_waiting_requester)
    VALUES (N'High - 4 hours', 'HIGH', 240, 0);
IF NOT EXISTS (SELECT 1 FROM dbo.sla_policies WHERE policy_name = N'Urgent - 1 hour')
    INSERT INTO dbo.sla_policies
        (policy_name, priority, resolution_minutes, pause_waiting_requester)
    VALUES (N'Urgent - 1 hour', 'URGENT', 60, 0);

DECLARE @projector_skill BIGINT = (SELECT id FROM dbo.skills WHERE code = 'PROJECTOR');
DECLARE @hvac_skill BIGINT = (SELECT id FROM dbo.skills WHERE code = 'HVAC');
DECLARE @plumbing_skill BIGINT = (SELECT id FROM dbo.skills WHERE code = 'PLUMBING');
DECLARE @safety_skill BIGINT = (SELECT id FROM dbo.skills WHERE code = 'SAFETY');
DECLARE @normal_sla BIGINT = (
    SELECT id FROM dbo.sla_policies WHERE policy_name = N'Normal - 24 hours'
);
DECLARE @high_sla BIGINT = (
    SELECT id FROM dbo.sla_policies WHERE policy_name = N'High - 4 hours'
);
DECLARE @urgent_sla BIGINT = (
    SELECT id FROM dbo.sla_policies WHERE policy_name = N'Urgent - 1 hour'
);

UPDATE form_version
SET form_version.requires_equipment = 1,
    form_version.min_attachment_count = 1,
    form_version.default_skill_id = @projector_skill,
    form_version.default_sla_policy_id = @normal_sla
FROM dbo.category_form_versions AS form_version
JOIN dbo.incident_categories AS category
    ON category.id = form_version.category_id
WHERE category.code = 'PROJECTOR_ISSUE'
  AND form_version.version_number = 1
  AND form_version.form_status = 'PUBLISHED';

UPDATE form_version
SET form_version.requires_equipment = 0,
    form_version.min_attachment_count = 0,
    form_version.default_skill_id = @hvac_skill,
    form_version.default_sla_policy_id = @normal_sla
FROM dbo.category_form_versions AS form_version
JOIN dbo.incident_categories AS category
    ON category.id = form_version.category_id
WHERE category.code = 'AIR_CONDITIONER_ISSUE'
  AND form_version.version_number = 1
  AND form_version.form_status = 'PUBLISHED';

UPDATE form_version
SET form_version.requires_equipment = 0,
    form_version.min_attachment_count = 1,
    form_version.default_skill_id = @plumbing_skill,
    form_version.default_sla_policy_id = @high_sla
FROM dbo.category_form_versions AS form_version
JOIN dbo.incident_categories AS category
    ON category.id = form_version.category_id
WHERE category.code = 'WATER_LEAK'
  AND form_version.version_number = 1
  AND form_version.form_status = 'PUBLISHED';

UPDATE form_version
SET form_version.requires_equipment = 0,
    form_version.min_attachment_count = 0,
    form_version.default_skill_id = @safety_skill,
    form_version.default_sla_policy_id = @urgent_sla
FROM dbo.category_form_versions AS form_version
JOIN dbo.incident_categories AS category
    ON category.id = form_version.category_id
WHERE category.code = 'SAFETY_INCIDENT'
  AND form_version.version_number = 1
  AND form_version.form_status = 'PUBLISHED';


GO

/* ===== END V7__seed_skill_and_sla_reference_data.sql ===== */


/* ===== BEGIN V8__remove_document_data_tables_from_sql.sql ===== */
SET NOCOUNT ON;

DECLARE @audit_count BIGINT = (SELECT COUNT_BIG(*) FROM dbo.audit_logs);
DECLARE @comment_count BIGINT = (SELECT COUNT_BIG(*) FROM dbo.ticket_comments);
DECLARE @notification_count BIGINT = (SELECT COUNT_BIG(*) FROM dbo.notifications);
DECLARE @verification NVARCHAR(4000) = CONVERT(NVARCHAR(4000), (
    SELECT value
    FROM sys.extended_properties
    WHERE class = 0
      AND name = N'NexoraMongoBackfillVerified'
));

IF (@audit_count + @comment_count + @notification_count) > 0
   AND (
       @verification IS NULL
       OR ISJSON(@verification) <> 1
       OR TRY_CONVERT(BIGINT, JSON_VALUE(@verification, '$.auditLogs')) <> @audit_count
       OR TRY_CONVERT(BIGINT, JSON_VALUE(@verification, '$.ticketComments')) <> @comment_count
       OR TRY_CONVERT(BIGINT, JSON_VALUE(@verification, '$.notifications')) <> @notification_count
   )
BEGIN
    THROW 51008,
        'MongoDB backfill is not verified. Run the documented backfill before applying V8.',
        1;
END;

DROP TABLE IF EXISTS dbo.audit_logs;
DROP TABLE IF EXISTS dbo.ticket_comments;
DROP TABLE IF EXISTS dbo.notifications;

IF EXISTS (
    SELECT 1
    FROM sys.extended_properties
    WHERE class = 0
      AND name = N'NexoraMongoBackfillVerified'
)
BEGIN
    EXEC sys.sp_dropextendedproperty @name = N'NexoraMongoBackfillVerified';
END;
GO

/* ===== END V8__remove_document_data_tables_from_sql.sql ===== */


/* ===== NEXORA FULL SCHEMA COMPLETED ===== */

SELECT DB_NAME() AS database_name,
       COUNT(*) AS application_table_count
FROM sys.tables
WHERE schema_id = SCHEMA_ID(N'dbo');

SELECT
    (SELECT COUNT(*) FROM dbo.roles) AS role_count,
    (SELECT COUNT(*) FROM dbo.incident_categories) AS category_count,
    (SELECT COUNT(*) FROM dbo.skills) AS skill_count,
    (SELECT COUNT(*) FROM dbo.sla_policies) AS sla_policy_count;

DBCC CHECKCONSTRAINTS WITH ALL_CONSTRAINTS;
GO
