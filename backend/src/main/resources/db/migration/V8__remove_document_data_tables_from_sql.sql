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
        'MongoDB backfill is not verified. Run the documented backfill with Flyway target 7 before applying V8.',
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
