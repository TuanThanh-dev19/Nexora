package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class SqlServerMigrationIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void assignmentEndReasonStoresOneThousandUnicodeCharacters() {
		assertThat(jdbcTemplate.queryForObject("""
				SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
				WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'ticket_assignments' AND COLUMN_NAME = 'end_reason'
				""", Integer.class)).isEqualTo(1000);
	}

	@Test
	void flywayCreatesTheSqlServerSchemaAndSeedData() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM sys.tables
				WHERE schema_id = SCHEMA_ID('dbo')
				  AND name IN (
					'app_users', 'roles', 'user_roles',
					'locations', 'equipment_types', 'assets', 'asset_status_history',
					'incident_categories', 'category_form_versions',
					'field_definitions', 'field_options',
					'tickets', 'ticket_field_values', 'ticket_assignments',
					'ticket_status_history', 'work_logs', 'ticket_attachments',
					'ticket_feedback', 'ticket_accuracy_reviews',
					'technician_profiles', 'skills',
					'technician_skills', 'technician_service_areas', 'sla_policies',
					'violation_cases', 'violation_appeals'
				)
				""", Integer.class);
		Integer removedDocumentTableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM sys.tables
				WHERE schema_id = SCHEMA_ID('dbo')
				  AND name IN ('audit_logs', 'ticket_comments', 'notifications')
				""", Integer.class);
		Integer totalApplicationTableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM sys.tables
				WHERE schema_id = SCHEMA_ID('dbo')
				  AND is_ms_shipped = 0
				  AND name <> 'flyway_schema_history'
				""", Integer.class);
		Integer roleCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM dbo.roles", Integer.class);
		Integer categoryCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM dbo.incident_categories", Integer.class);
		Integer skillCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM dbo.skills", Integer.class);
		Integer slaPolicyCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM dbo.sla_policies", Integer.class);
		Integer configuredFormCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM dbo.category_form_versions
				WHERE default_skill_id IS NOT NULL
				  AND default_sla_policy_id IS NOT NULL
				""", Integer.class);
		Integer requiredForeignKeyCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM sys.foreign_keys
				WHERE name IN (
					'fk_user_roles_assigned_by',
					'fk_technician_profiles_user',
					'fk_technician_skills_technician',
					'fk_technician_skills_skill',
					'fk_technician_service_areas_technician',
					'fk_technician_service_areas_location',
					'fk_category_form_versions_default_skill',
					'fk_category_form_versions_default_sla',
					'fk_tickets_required_skill',
					'fk_ticket_attachments_ticket_form',
					'fk_ticket_attachments_definition_form',
					'fk_violation_cases_ticket',
					'fk_violation_appeals_case'
				)
				""", Integer.class);
		Integer workforceCheckConstraintCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM sys.check_constraints
				WHERE name IN (
					'ck_technician_profiles_max_active',
					'ck_technician_skills_proficiency'
				)
				""", Integer.class);
		Integer normalizedEmailComputedColumnCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM sys.computed_columns
				WHERE object_id = OBJECT_ID('dbo.app_users')
				  AND name = 'normalized_email'
				  AND is_persisted = 1
				""", Integer.class);
		Integer nullableAssignedByColumnCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM sys.columns
				WHERE object_id = OBJECT_ID('dbo.user_roles')
				  AND name = 'assigned_by'
				  AND is_nullable = 1
				""", Integer.class);
		Integer requiredIndexCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM sys.indexes
				WHERE name IN (
					'uq_app_users_normalized_email',
					'uq_assets_serial_number',
					'uq_category_form_versions_one_published',
					'uq_ticket_assignments_one_active',
					'idx_tickets_required_skill_status',
					'idx_technician_skills_skill',
					'idx_technician_service_areas_location'
				)
				  AND is_disabled = 0
				""", Integer.class);
		Integer latestSuccessfulMigration = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM dbo.flyway_schema_history
				WHERE version = '9' AND success = 1
				""", Integer.class);

		assertThat(tableCount).isEqualTo(26);
		assertThat(totalApplicationTableCount).isEqualTo(26);
		assertThat(removedDocumentTableCount).isZero();
		assertThat(roleCount).isEqualTo(4);
		assertThat(categoryCount).isGreaterThanOrEqualTo(4);
		assertThat(skillCount).isGreaterThanOrEqualTo(4);
		assertThat(slaPolicyCount).isGreaterThanOrEqualTo(4);
		assertThat(configuredFormCount).isGreaterThanOrEqualTo(4);
		assertThat(requiredForeignKeyCount).isEqualTo(13);
		assertThat(workforceCheckConstraintCount).isEqualTo(2);
		assertThat(normalizedEmailComputedColumnCount).isEqualTo(1);
		assertThat(nullableAssignedByColumnCount).isEqualTo(1);
		assertThat(requiredIndexCount).isEqualTo(7);
		assertThat(latestSuccessfulMigration).isEqualTo(1);
	}
}
