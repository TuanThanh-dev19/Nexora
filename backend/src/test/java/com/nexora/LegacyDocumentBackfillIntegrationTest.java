package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.nexora.audit.repository.AuditEventRepository;
import com.nexora.migration.LegacyDocumentBackfillRunner;
import com.nexora.notification.repository.NotificationRepository;
import com.nexora.ticket.repository.TicketCommentRepository;

@SpringBootTest(properties = {
        "spring.flyway.target=7",
        "spring.data.mongodb.auto-index-creation=true",
        "app.mongodb.backfill.enabled=true"
})
@Import(LegacyDocumentBackfillIntegrationTest.LegacySeedConfiguration.class)
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_MIGRATION_IT", matches = "(?i)true")
class LegacyDocumentBackfillIntegrationTest {

    @Container
    static final MSSQLServerContainer<?> SQL_SERVER = new MSSQLServerContainer<>(
            DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-CU26-ubuntu-22.04"))
            .acceptLicense();

    @Container
    static final MongoDBContainer MONGODB = new MongoDBContainer(
            DockerImageName.parse("mongo:8.0"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
        registry.add("spring.data.mongodb.uri",
                () -> MONGODB.getReplicaSetUrl("nexora_migration_test"));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LegacyDocumentBackfillRunner backfillRunner;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void backfillsIdempotentlyVerifiesCountsAndAllowsV8ToDropSqlTables() throws Exception {
        assertThat(auditEventRepository.findByLegacySqlId(1L)).isPresent();
        assertThat(ticketCommentRepository.findByLegacySqlId(1L)).isPresent();
        assertThat(notificationRepository.findByLegacySqlId(1L)).isPresent();

        ApplicationArguments noArguments = new DefaultApplicationArguments(new String[0]);
        backfillRunner.run(noArguments);
        assertThat(auditEventRepository.count()).isEqualTo(1);
        assertThat(ticketCommentRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.count()).isEqualTo(1);

        String verification = jdbcTemplate.queryForObject("""
                SELECT CONVERT(NVARCHAR(4000), value)
                FROM sys.extended_properties
                WHERE class = 0 AND name = N'NexoraMongoBackfillVerified'
                """, String.class);
        assertThat(verification)
                .contains("\"auditLogs\":1")
                .contains("\"ticketComments\":1")
                .contains("\"notifications\":1");

        Flyway.configure()
                .dataSource(SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Integer legacyTables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys.tables
                WHERE schema_id = SCHEMA_ID('dbo')
                  AND name IN ('audit_logs', 'ticket_comments', 'notifications')
                """, Integer.class);
        assertThat(legacyTables).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LegacySeedConfiguration {

        @Bean
        @Order(0)
        ApplicationRunner seedLegacyDocumentRows(JdbcTemplate jdbcTemplate) {
            return args -> jdbcTemplate.execute("""
                    DECLARE @user_id BIGINT;
                    DECLARE @ticket_id BIGINT;
                    DECLARE @category_id BIGINT = (
                        SELECT id FROM dbo.incident_categories WHERE code = 'WATER_LEAK'
                    );
                    DECLARE @form_id BIGINT = (
                        SELECT id FROM dbo.category_form_versions
                        WHERE category_id = @category_id AND version_number = 1
                    );
                    DECLARE @location_id BIGINT = (
                        SELECT id FROM dbo.locations WHERE code = 'ROOM-A101'
                    );

                    INSERT INTO dbo.app_users (email, password_hash, full_name)
                    VALUES ('migration-test@nexora.local', 'not-a-real-password-hash', N'Migration Test');
                    SET @user_id = SCOPE_IDENTITY();

                    INSERT INTO dbo.tickets (
                        ticket_number, reporter_id, category_id, form_version_id,
                        location_id, title, description
                    ) VALUES (
                        'MIGRATION-1', @user_id, @category_id, @form_id,
                        @location_id, N'Migration test ticket', N'Backfill integration fixture'
                    );
                    SET @ticket_id = SCOPE_IDENTITY();

                    INSERT INTO dbo.audit_logs (
                        actor_id, action, target_type, target_id, before_data, after_data, reason
                    ) VALUES (
                        @user_id, 'TICKET_UPDATED', 'TICKET', @ticket_id,
                        N'{"status":"SUBMITTED"}', N'{"status":"ASSIGNED"}', N'Integration test'
                    );
                    INSERT INTO dbo.ticket_comments (ticket_id, author_id, body, visibility)
                    VALUES (@ticket_id, @user_id, N'Legacy SQL comment', 'PUBLIC');
                    INSERT INTO dbo.notifications (
                        recipient_id, ticket_id, notification_type, title, message
                    ) VALUES (
                        @user_id, @ticket_id, 'TICKET_UPDATED', N'Ticket updated', N'Legacy notification'
                    );
                    """);
        }
    }
}
