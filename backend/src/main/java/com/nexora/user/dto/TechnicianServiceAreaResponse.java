package com.nexora.user.dto;

import com.nexora.user.entity.TechnicianServiceArea;

public record TechnicianServiceAreaResponse(
        Long locationId,
        String locationCode,
        String locationName
) {
    public static TechnicianServiceAreaResponse from(TechnicianServiceArea tsa) {
        return new TechnicianServiceAreaResponse(
                tsa.getLocation().getId(),
                tsa.getLocation().getCode(),
                tsa.getLocation().getName()
        );
    }
}
