ALTER TABLE dbo.category_form_versions ADD lock_version BIGINT NOT NULL
    CONSTRAINT df_category_form_versions_lock_version DEFAULT 0;
ALTER TABLE dbo.category_form_versions ADD updated_at DATETIMEOFFSET(7) NOT NULL
    CONSTRAINT df_category_form_versions_updated_at DEFAULT SYSUTCDATETIME();
ALTER TABLE dbo.field_definitions ADD active BIT NOT NULL
    CONSTRAINT df_field_definitions_active DEFAULT 1;
