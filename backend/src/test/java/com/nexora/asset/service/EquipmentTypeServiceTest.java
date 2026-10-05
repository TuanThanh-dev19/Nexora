package com.nexora.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.nexora.asset.dto.EquipmentTypeCreateRequest;
import com.nexora.asset.dto.EquipmentTypeResponse;
import com.nexora.asset.dto.EquipmentTypeUpdateRequest;
import com.nexora.asset.repository.EquipmentTypeRepository;
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.testsupport.NexoraJpaTest;

@NexoraJpaTest
@Import(EquipmentTypeService.class)
class EquipmentTypeServiceTest {

    @Autowired
    private EquipmentTypeService equipmentTypeService;

    @Autowired
    private EquipmentTypeRepository equipmentTypeRepository;

    @Test
    void create_and_update_equipment_type_succeeds() {
        EquipmentTypeResponse created = equipmentTypeService.create(
                new EquipmentTypeCreateRequest("EQ-01", "Air Conditioner", "Cooling unit"));

        assertThat(created.id()).isNotNull();
        assertThat(created.code()).isEqualTo("EQ-01");
        assertThat(created.name()).isEqualTo("Air Conditioner");
        assertThat(created.description()).isEqualTo("Cooling unit");
        assertThat(created.active()).isTrue();

        EquipmentTypeResponse updated = equipmentTypeService.update(
                created.id(),
                new EquipmentTypeUpdateRequest("Air Conditioner V2", "Updated unit", false));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.name()).isEqualTo("Air Conditioner V2");
        assertThat(updated.description()).isEqualTo("Updated unit");
        assertThat(updated.active()).isFalse();
    }

    @Test
    void create_with_duplicate_code_throws_business_rule_exception() {
        equipmentTypeService.create(
                new EquipmentTypeCreateRequest("EQ-DUP", "First", null));

        assertThatThrownBy(() ->
                equipmentTypeService.create(
                        new EquipmentTypeCreateRequest("EQ-DUP", "Second", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("EQ-DUP");
    }

    @Test
    void list_with_active_filter_excludes_inactive() {
        EquipmentTypeResponse active = equipmentTypeService.create(
                new EquipmentTypeCreateRequest("EQ-ACT", "Active", null));
        EquipmentTypeResponse inactive = equipmentTypeService.create(
                new EquipmentTypeCreateRequest("EQ-INACT", "Inactive", null));

        equipmentTypeService.update(inactive.id(),
                new EquipmentTypeUpdateRequest("Inactive", null, false));

        var activeResults = equipmentTypeService.list(true);
        var allResults = equipmentTypeService.list(false);

        assertThat(activeResults).extracting(EquipmentTypeResponse::code)
                .contains("EQ-ACT")
                .doesNotContain("EQ-INACT");

        assertThat(allResults).extracting(EquipmentTypeResponse::code)
                .contains("EQ-ACT", "EQ-INACT");
    }
}
