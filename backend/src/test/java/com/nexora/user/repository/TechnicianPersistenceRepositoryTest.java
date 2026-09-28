package com.nexora.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.location.repository.LocationRepository;
import com.nexora.testsupport.NexoraJpaTest;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Skill;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.TechnicianServiceArea;
import com.nexora.user.entity.TechnicianSkill;

import jakarta.persistence.Persistence;

@NexoraJpaTest
class TechnicianPersistenceRepositoryTest {

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
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsProfileAsSharedPrimaryKeyExtensionOfExistingUser() {
        AppUser user = saveUser("profile@example.test");
        TechnicianProfile profile = technicians.saveAndFlush(new TechnicianProfile(user));
        entityManager.clear();

        TechnicianProfile reloaded = technicians.findById(user.getId()).orElseThrow();

        assertThat(reloaded.getTechnicianId()).isEqualTo(user.getId());
        assertThat(reloaded.getUser().getId()).isEqualTo(user.getId());
        assertThat(reloaded.isAvailable()).isTrue();
        assertThat(reloaded.getMaxActiveTickets())
                .isEqualTo(TechnicianProfile.DEFAULT_MAX_ACTIVE_TICKETS);
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(profile.getTechnicianId()).isEqualTo(user.getId());
    }

    @Test
    void requiresPersistedUserAndPositiveCapacity() {
        AppUser unsavedUser = new AppUser(
                "unsaved@example.test", TEST_PASSWORD_HASH, "Unsaved Technician");

        assertThatThrownBy(() -> new TechnicianProfile(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("user is required");
        assertThatThrownBy(() -> new TechnicianProfile(unsavedUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("persisted");

        AppUser savedUser = saveUser("capacity@example.test");
        assertThatThrownBy(() -> new TechnicianProfile(savedUser, 0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> new TechnicianProfile(savedUser, -1, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");

        TechnicianProfile profile = technicians.saveAndFlush(
                new TechnicianProfile(savedUser, 8, false));
        assertThat(profile.getMaxActiveTickets()).isEqualTo(8);
        assertThat(profile.isAvailable()).isFalse();
        assertThatThrownBy(() -> profile.changeMaxActiveTickets(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadsMultipleSkillAssignmentsAndPreservesAssignmentTimestamps() {
        TechnicianProfile technician = saveTechnician("skills@example.test");
        Skill electrical = skills.saveAndFlush(new Skill("ELECTRICAL", "Electrical"));
        Skill plumbing = skills.saveAndFlush(new Skill("PLUMBING-TEST", "Plumbing"));
        OffsetDateTime assignedAt = OffsetDateTime.of(
                2026, 9, 28, 8, 30, 0, 123_456_000, ZoneOffset.UTC);

        technicianSkills.save(new TechnicianSkill(technician, electrical, 2, assignedAt));
        technicianSkills.saveAndFlush(new TechnicianSkill(
                technician, plumbing, 5, assignedAt.plusMinutes(1)));
        entityManager.clear();

        TechnicianProfile loaded = technicians
                .findByIdWithSkills(technician.getTechnicianId())
                .orElseThrow();
        assertThat(Persistence.getPersistenceUtil().isLoaded(loaded, "skillAssignments"))
                .isTrue();
        assertThat(loaded.getSkillAssignments())
                .allSatisfy(assignment -> assertThat(
                        Persistence.getPersistenceUtil().isLoaded(assignment, "skill")).isTrue());
        entityManager.clear();

        assertThat(loaded.getSkillAssignments())
                .extracting(assignment -> assignment.getSkill().getCode())
                .containsExactlyInAnyOrder("ELECTRICAL", "PLUMBING-TEST");
        assertThat(loaded.getSkillAssignments())
                .extracting(TechnicianSkill::getProficiencyLevel)
                .containsExactlyInAnyOrder(2, 5);
        assertThat(loaded.getSkillAssignments())
                .extracting(TechnicianSkill::getAssignedAt)
                .containsExactlyInAnyOrder(assignedAt, assignedAt.plusMinutes(1));
    }

    @Test
    void acceptsOnlyProficiencyLevelsFromOneThroughFive() {
        TechnicianProfile technician = saveTechnician("proficiency@example.test");
        Skill minimum = skills.saveAndFlush(new Skill("PROFICIENCY-MIN", "Minimum"));
        Skill maximum = skills.saveAndFlush(new Skill("PROFICIENCY-MAX", "Maximum"));

        technicianSkills.save(new TechnicianSkill(technician, minimum, 1));
        technicianSkills.saveAndFlush(new TechnicianSkill(technician, maximum, 5));

        assertThat(technicianSkills.count()).isEqualTo(2);
        assertThatThrownBy(() -> new TechnicianSkill(technician, minimum, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
        assertThatThrownBy(() -> new TechnicianSkill(technician, minimum, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
    }

    @Test
    void loadsConfiguredServiceAreasAndChecksOnlyDirectCoverage() {
        TechnicianProfile technician = saveTechnician("areas@example.test");
        Location campus = locations.saveAndFlush(new Location(
                "COVERAGE-CAMPUS", "Coverage Campus", LocationType.CAMPUS, null));
        Location building = locations.saveAndFlush(new Location(
                "COVERAGE-BUILDING", "Coverage Building", LocationType.BUILDING, campus));
        Location room = locations.saveAndFlush(new Location(
                "COVERAGE-ROOM", "Coverage Room", LocationType.ROOM, building));
        Location other = locations.saveAndFlush(new Location(
                "OTHER-CAMPUS", "Other Campus", LocationType.CAMPUS, null));

        serviceAreas.save(new TechnicianServiceArea(technician, campus));
        serviceAreas.saveAndFlush(new TechnicianServiceArea(technician, other));
        entityManager.clear();

        TechnicianProfile loaded = technicians
                .findByIdWithServiceAreas(technician.getTechnicianId())
                .orElseThrow();
        assertThat(Persistence.getPersistenceUtil().isLoaded(loaded, "serviceAreas"))
                .isTrue();
        assertThat(loaded.getServiceAreas())
                .allSatisfy(serviceArea -> assertThat(
                        Persistence.getPersistenceUtil().isLoaded(serviceArea, "location")).isTrue());
        entityManager.clear();

        assertThat(loaded.getServiceAreas())
                .extracting(serviceArea -> serviceArea.getLocation().getCode())
                .containsExactlyInAnyOrder("COVERAGE-CAMPUS", "OTHER-CAMPUS");
        assertThat(serviceAreas.existsDirectCoverage(
                technician.getTechnicianId(), campus.getId())).isTrue();
        assertThat(serviceAreas.existsDirectCoverage(
                technician.getTechnicianId(), room.getId())).isFalse();
        assertThat(serviceAreas.existsDirectCoverage(
                technician.getTechnicianId(), building.getId())).isFalse();
    }

    @Test
    void returnsOnlyActiveSkillsInDeterministicOrder() {
        skills.saveAndFlush(new Skill("SKILL-Z", "Same Skill"));
        skills.saveAndFlush(new Skill("SKILL-A", "Same Skill"));
        Skill inactive = new Skill("SKILL-OFF", "A Skill");
        inactive.changeActive(false);
        skills.saveAndFlush(inactive);
        entityManager.clear();

        assertThat(skills.findActiveOrderByNameAndCode())
                .extracting(Skill::getCode)
                .containsExactly("SKILL-A", "SKILL-Z");
    }

    @Test
    void incrementsVersionWhenProfileChanges() {
        TechnicianProfile technician = saveTechnician("version@example.test");
        Long initialVersion = technician.getVersion();

        technician.changeAvailability(false);
        technician.changeMaxActiveTickets(7);
        technicians.flush();

        assertThat(technician.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(technician.getUpdatedAt()).isAfterOrEqualTo(technician.getCreatedAt());
    }

    @Test
    void rejectsAStaleProfileUpdate() {
        TechnicianProfile staleProfile = saveTechnician("stale-profile@example.test");
        entityManager.detach(staleProfile);

        jdbcTemplate.update("""
                UPDATE dbo.technician_profiles
                SET available = ?, version = version + 1
                WHERE technician_id = ?
                """, false, staleProfile.getTechnicianId());
        staleProfile.changeMaxActiveTickets(9);

        assertThatThrownBy(() -> technicians.saveAndFlush(staleProfile))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private TechnicianProfile saveTechnician(String email) {
        return technicians.saveAndFlush(new TechnicianProfile(saveUser(email)));
    }

    private AppUser saveUser(String email) {
        return users.saveAndFlush(new AppUser(
                email, TEST_PASSWORD_HASH, "Test Technician"));
    }
}
