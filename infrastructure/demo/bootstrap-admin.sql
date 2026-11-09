-- Local demo only. Never applied by Flyway or normal application startup.
-- Password: Demo@Nexora2026! (BCrypt, cost 10). Existing accounts are untouched.
SET XACT_ABORT ON;
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF NOT EXISTS (SELECT 1 FROM dbo.app_users WITH (UPDLOCK, HOLDLOCK)
               WHERE normalized_email = 'admin@demo.nexora.test')
BEGIN
    INSERT dbo.app_users (email, password_hash, full_name, status)
    VALUES ('admin@demo.nexora.test',
            '$2b$10$5IxioG8qhjD.jD9LBrvyvuWbjyEAOMzMwUnPlhVetufWbQ6DdwqYW',
            N'Nguyễn Minh Anh · Quản trị demo', 'ACTIVE');
    DECLARE @admin_id BIGINT = SCOPE_IDENTITY();
    INSERT dbo.user_roles (user_id, role_id)
    SELECT @admin_id, id FROM dbo.roles WHERE code = 'ADMIN';
    IF @@ROWCOUNT <> 1 THROW 51000, 'ADMIN role missing. Start backend/Flyway first.', 1;
END;
COMMIT;
