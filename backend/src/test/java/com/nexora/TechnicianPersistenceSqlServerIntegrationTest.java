package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.location.repository.LocationRepository;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Skill;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.TechnicianServiceArea;
import com.nexora.user.entity.TechnicianSkill;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.SkillRepository;
import com.nexora.user.repository.TechnicianProfileRepository;
import com.nexora.user.repository.TechnicianServiceAreaRepository;
import com.nexora.user.repository.TechnicianSkillRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class TechnicianPersistenceSqlServerIntegrationTest {

    private static final String TEST_PASSWORD_HASH = "test-only-password-hash";

    @Autowired
    private AppUserRepository users;

    @Autowired
    private TechnicianProfileRepository technicians;

    @Autowired
    private SkillRepository skills;

    @Autowired
    private TechnicianSkillRepository technicianSkills;

    @Autowired
    private TechnicianServiceAreaRepository serviceAreas;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mapsWorkforceTablesUsingExistingIdentitySkillAndLocationFoundations() {
        AppUser user = users.saveAndFlush(new AppUser(
                "sql-technician@example.test",
                TEST_PASSWORD_HASH,
                "SQL Technician"));
        TechnicianProfile technician = technicians.saveAndFlush(
                new TechnicianProfile(user, 6, true));
        Skill skill = skills.saveAndFlush(new Skill(
                "SQL-ELECTRICAL", "Kỹ thuật điện"));
        Location campus = locations.saveAndFlush(new Location(
                "SQL-COVERAGE-CAMPUS",
                "Cơ sở kiểm thử",
                LocationType.CAMPUS,
                null));
        OffsetDateTime assignedAt = OffsetDateTime.of(
                2026, 9, 28, 9, 15, 0, 123_456_700, ZoneOffset.ofHours(7));

        technicianSkills.saveAndFlush(new TechnicianSkill(
                technician, skill, 4, assignedAt));
        serviceAreas.saveAndFlush(new TechnicianServiceArea(technician, campus));
        entityManager.clear();

        TechnicianProfile withSkills = technicians
                .findByIdWithSkills(user.getId())
                .orElseThrow();
        assertThat(Persistence.getPersistenceUtil().isLoaded(
                withSkills, "skillAssignments")).isTrue();
        assertThat(withSkills.getSkillAssignments()).singleElement().satisfies(assignment -> {
            assertThat(assignment.getSkill().getName()).isEqualTo("Kỹ thuật điện");
            assertThat(assignment.getProficiencyLevel()).isEqualTo(4);
            assertThat(assignment.getAssignedAt()).isEqualTo(assignedAt);
        });
        entityManager.clear();

        TechnicianProfile withAreas = technicians
                .findByIdWithServiceAreas(user.getId())
                .orElseThrow();
        assertThat(withAreas.getTechnicianId()).isEqualTo(user.getId());
        assertThat(withAreas.getUser().getId()).isEqualTo(user.getId());
        assertThat(withAreas.getServiceAreas()).singleElement()
                .extracting(area -> area.getLocation().getName())
                .isEqualTo("Cơ sở kiểm thử");
        assertThat(serviceAreas.existsDirectCoverage(user.getId(), campus.getId())).isTrue();

        Long initialVersion = withAreas.getVersion();
        withAreas.changeAvailability(false);
        technicians.flush();

        assertThat(withAreas.getVersion()).isEqualTo(initialVersion + 1);

        Skill invalidProficiencySkill = skills.saveAndFlush(new Skill(
                "SQL-INVALID-PROFICIENCY", "Invalid proficiency test"));
        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE dbo.technician_profiles
                SET max_active_tickets = 0
                WHERE technician_id = ?
                """, user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO dbo.technician_skills
                    (technician_id, skill_id, proficiency_level)
                VALUES (?, ?, 0)
                """, user.getId(), invalidProficiencySkill.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
