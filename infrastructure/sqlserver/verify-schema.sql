SET NOCOUNT ON;

DECLARE @expected_tables TABLE (
    table_name SYSNAME NOT NULL PRIMARY KEY
);

INSERT INTO @expected_tables (table_name)
VALUES
    ('app_users'), ('roles'), ('user_roles'),
    ('technician_profiles'), ('skills'), ('technician_skills'),
    ('technician_service_areas'), ('locations'), ('equipment_types'),
    ('assets'), ('asset_status_history'), ('incident_categories'),
    ('category_form_versions'), ('field_definitions'), ('field_options'),
    ('sla_policies'), ('tickets'), ('ticket_field_values'),
    ('ticket_assignments'), ('ticket_status_history'), ('work_logs'),
    ('ticket_attachments'), ('ticket_feedback'),
    ('ticket_accuracy_reviews'),
    ('violation_cases'), ('violation_appeals');

IF EXISTS (
    SELECT 1
    FROM @expected_tables expected
    WHERE OBJECT_ID(N'dbo.' + expected.table_name, N'U') IS NULL
)
BEGIN
    SELECT expected.table_name AS missing_table
    FROM @expected_tables expected
    WHERE OBJECT_ID(N'dbo.' + expected.table_name, N'U') IS NULL
    ORDER BY expected.table_name;

    THROW 51000, 'Nexora schema is incomplete. Run the backend so Flyway can apply every migration.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM sys.tables
    WHERE schema_id = SCHEMA_ID(N'dbo')
      AND name IN (N'audit_logs', N'ticket_comments', N'notifications')
)
    THROW 51001, 'Legacy document tables must not remain in SQL Server after V8.', 1;

DECLARE @expected_foreign_keys TABLE (constraint_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @expected_foreign_keys (constraint_name)
VALUES
    ('fk_technician_profiles_user'),
    ('fk_technician_skills_technician'),
    ('fk_technician_service_areas_location'),
    ('fk_category_form_versions_default_skill'),
    ('fk_category_form_versions_default_sla'),
    ('fk_tickets_required_skill'),
    ('fk_ticket_attachments_ticket_form'),
    ('fk_ticket_attachments_definition_form'),
    ('fk_violation_cases_ticket'),
    ('fk_violation_appeals_case');

DECLARE @expected_check_constraints TABLE (constraint_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @expected_check_constraints (constraint_name)
VALUES
    ('ck_technician_profiles_max_active'),
    ('ck_technician_skills_proficiency'),
    ('ck_sla_policies_resolution_minutes'),
    ('ck_tickets_resolution_outcome'),
    ('ck_ticket_assignments_accept_before_end'),
    ('ck_violation_cases_review_metadata'),
    ('ck_violation_appeals_review_metadata');

DECLARE @expected_indexes TABLE (index_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @expected_indexes (index_name)
VALUES
    ('uq_app_users_normalized_email'),
    ('uq_assets_serial_number'),
    ('uq_category_form_versions_one_published'),
    ('uq_ticket_assignments_one_active'),
    ('idx_tickets_required_skill_status');

IF EXISTS (
    SELECT 1
    FROM @expected_foreign_keys expected
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys actual
        WHERE actual.name = expected.constraint_name
          AND actual.is_disabled = 0
          AND actual.is_not_trusted = 0
    )
)
BEGIN
    SELECT expected.constraint_name AS missing_disabled_or_untrusted_foreign_key
    FROM @expected_foreign_keys expected
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.foreign_keys actual
        WHERE actual.name = expected.constraint_name
          AND actual.is_disabled = 0
          AND actual.is_not_trusted = 0
    );
    THROW 51002, 'Nexora schema has a missing, disabled, or untrusted required foreign key.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM @expected_check_constraints expected
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.check_constraints actual
        WHERE actual.name = expected.constraint_name
          AND actual.is_disabled = 0
          AND actual.is_not_trusted = 0
    )
)
BEGIN
    SELECT expected.constraint_name AS missing_disabled_or_untrusted_check_constraint
    FROM @expected_check_constraints expected
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.check_constraints actual
        WHERE actual.name = expected.constraint_name
          AND actual.is_disabled = 0
          AND actual.is_not_trusted = 0
    );
    THROW 51003, 'Nexora schema has a missing, disabled, or untrusted required check constraint.', 1;
END;

IF EXISTS (
    SELECT 1
    FROM @expected_indexes expected
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.indexes actual
        WHERE actual.name = expected.index_name AND actual.is_disabled = 0
    )
)
BEGIN
    SELECT expected.index_name AS missing_or_disabled_index
    FROM @expected_indexes expected
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.indexes actual
        WHERE actual.name = expected.index_name AND actual.is_disabled = 0
    );
    THROW 51004, 'Nexora schema is missing or has disabled a required index.', 1;
END;

IF NOT EXISTS (
    SELECT 1
    FROM dbo.flyway_schema_history
    WHERE version = '8' AND success = 1
)
    THROW 51005, 'Flyway version 8 was not applied successfully.', 1;

SELECT DB_NAME() AS database_name,
       COUNT(*) AS verified_application_table_count
FROM sys.tables actual
WHERE actual.schema_id = SCHEMA_ID(N'dbo')
  AND actual.name IN (SELECT table_name FROM @expected_tables);

IF (SELECT COUNT(*) FROM @expected_tables) <> 26
    THROW 51006, 'Schema verifier must define exactly 26 application tables.', 1;

IF (
    SELECT COUNT(*)
    FROM sys.tables
    WHERE schema_id = SCHEMA_ID(N'dbo')
      AND is_ms_shipped = 0
      AND name <> N'flyway_schema_history'
) <> 26
    THROW 51007, 'SQL Server must contain exactly 26 Nexora application tables after V8.', 1;

SELECT installed_rank, version, description, success
FROM dbo.flyway_schema_history
ORDER BY installed_rank;

SELECT
    (SELECT COUNT(*) FROM dbo.roles) AS role_count,
    (SELECT COUNT(*) FROM dbo.incident_categories) AS category_count,
    (SELECT COUNT(*) FROM dbo.skills) AS skill_count,
    (SELECT COUNT(*) FROM dbo.sla_policies) AS sla_policy_count;

DBCC CHECKCONSTRAINTS WITH ALL_CONSTRAINTS;
