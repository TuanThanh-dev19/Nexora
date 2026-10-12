package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.asset.entity.EquipmentType;
import com.nexora.asset.repository.EquipmentTypeRepository;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.location.repository.LocationRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class LocationEquipmentSqlServerIntegrationTest {

    @Autowired
    private LocationRepository locations;

    @Autowired
    private EquipmentTypeRepository equipmentTypes;

    @Autowired
    private EntityManager entityManager;

    @Test
    void mapsExistingTablesAndUnicodeFields() {
        Location campus = new Location("IT-CAMPUS", "Cơ sở phía Tây", LocationType.CAMPUS, null);
        campus.changeDescription("Khuôn viên kiểm thử");
        locations.saveAndFlush(campus);
        Location building = locations.saveAndFlush(new Location(
                "IT-BUILDING", "Tòa B", LocationType.BUILDING, campus));
        EquipmentType equipmentType = new EquipmentType("IT-PROJECTOR", "Máy chiếu");
        equipmentType.changeBrandAndModel("Hãng thử nghiệm", "Mẫu 1");
        equipmentTypes.saveAndFlush(equipmentType);
        entityManager.clear();

        assertThat(locations.findActiveDirectChildren(campus.getId()))
                .extracting(Location::getId).containsExactly(building.getId());
        assertThat(locations.findByCode("IT-CAMPUS")).get()
                .extracting(Location::getDescription).isEqualTo("Khuôn viên kiểm thử");
        assertThat(equipmentTypes.findById(equipmentType.getId())).get()
                .extracting(EquipmentType::getBrand).isEqualTo("Hãng thử nghiệm");
    }
}
