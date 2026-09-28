package com.nexora.asset.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.nexora.asset.entity.EquipmentType;
import com.nexora.testsupport.NexoraJpaTest;

@NexoraJpaTest
class EquipmentTypeRepositoryTest {

    @Autowired
    private EquipmentTypeRepository equipmentTypes;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsReferenceFieldsAndOrdersOnlyActiveEquipmentTypes() {
        EquipmentType zType = new EquipmentType("PROJECTOR-Z", "Projector");
        zType.changeDescription("Ceiling mounted projector");
        zType.changeBrandAndModel("Epson", "EB-2250U");
        equipmentTypes.saveAndFlush(zType);
        equipmentTypes.saveAndFlush(new EquipmentType("PROJECTOR-A", "Projector"));
        EquipmentType inactive = new EquipmentType("AIRCON", "Air conditioner");
        inactive.changeActive(false);
        equipmentTypes.saveAndFlush(inactive);
        entityManager.clear();

        assertThat(equipmentTypes.findActiveOrderByNameAndCode())
                .extracting(EquipmentType::getCode)
                .containsExactly("PROJECTOR-A", "PROJECTOR-Z");
        EquipmentType reloaded = equipmentTypes.findById(zType.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Projector");
        assertThat(reloaded.getDescription()).isEqualTo("Ceiling mounted projector");
        assertThat(reloaded.getBrand()).isEqualTo("Epson");
        assertThat(reloaded.getModel()).isEqualTo("EB-2250U");
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(equipmentTypes.findById(inactive.getId())).get()
                .extracting(EquipmentType::isActive).isEqualTo(false);
    }
}
