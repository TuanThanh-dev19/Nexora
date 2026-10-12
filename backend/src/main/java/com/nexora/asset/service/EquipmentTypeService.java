package com.nexora.asset.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.asset.dto.EquipmentTypeCreateRequest;
import com.nexora.asset.dto.EquipmentTypeResponse;
import com.nexora.asset.dto.EquipmentTypeUpdateRequest;
import com.nexora.asset.entity.EquipmentType;
import com.nexora.asset.repository.EquipmentTypeRepository;
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.ResourceNotFoundException;

/**
 * Business logic for the EquipmentType reference catalog.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Unique code (CREATE + UPDATE)</li>
 *   <li>Soft-delete only (active = false)</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class EquipmentTypeService {

    private final EquipmentTypeRepository equipmentTypeRepository;

    public EquipmentTypeService(EquipmentTypeRepository equipmentTypeRepository) {
        this.equipmentTypeRepository = equipmentTypeRepository;
    }

    // =========================================================================
    // READ
    // =========================================================================

    /**
     * Returns equipment types, filtered by active flag when requested.
     */
    public List<EquipmentTypeResponse> list(boolean activeOnly) {
        List<EquipmentType> types = activeOnly
                ? equipmentTypeRepository.findActiveOrderByNameAndCode()
                : equipmentTypeRepository.findAllOrderByNameAndCode();
        return types.stream().map(EquipmentTypeResponse::from).toList();
    }

    /**
     * Returns a single EquipmentType by ID.
     *
     * @throws ResourceNotFoundException when not found
     */
    public EquipmentTypeResponse getById(Long id) {
        return EquipmentTypeResponse.from(findOrThrow(id));
    }

    // =========================================================================
    // WRITE
    // =========================================================================

    /**
     * Creates a new EquipmentType.
     *
     * @throws BusinessRuleException when the code is already in use
     */
    @Transactional
    public EquipmentTypeResponse create(EquipmentTypeCreateRequest request) {
        validateUniqueCodeForCreate(request.code());

        EquipmentType equipmentType = new EquipmentType(
                request.code().strip(),
                request.name().strip());

        if (request.description() != null) {
            equipmentType.changeDescription(request.description().strip());
        }

        return EquipmentTypeResponse.from(equipmentTypeRepository.save(equipmentType));
    }

    /**
     * Updates an existing EquipmentType.
     *
     * @throws ResourceNotFoundException when not found
     * @throws BusinessRuleException     when the code is already in use by another record
     */
    @Transactional
    public EquipmentTypeResponse update(Long id, EquipmentTypeUpdateRequest request) {
        EquipmentType equipmentType = findOrThrow(id);

        equipmentType.changeName(request.name().strip());
        equipmentType.changeDescription(
                request.description() != null ? request.description().strip() : null);
        equipmentType.changeActive(request.active());

        return EquipmentTypeResponse.from(equipmentTypeRepository.save(equipmentType));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private EquipmentType findOrThrow(Long id) {
        return equipmentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EquipmentType not found: id=" + id));
    }

    private void validateUniqueCodeForCreate(String code) {
        equipmentTypeRepository.findByCode(code.strip()).ifPresent(existing -> {
            throw new BusinessRuleException(
                    "EquipmentType code already exists: '" + code + "'");
        });
    }
}
