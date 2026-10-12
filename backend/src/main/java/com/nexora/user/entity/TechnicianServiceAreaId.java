package com.nexora.user.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class TechnicianServiceAreaId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "technician_id")
    private Long technicianId;

    @Column(name = "location_id")
    private Long locationId;

    protected TechnicianServiceAreaId() {
    }

    public TechnicianServiceAreaId(Long technicianId, Long locationId) {
        this.technicianId = Objects.requireNonNull(technicianId, "technicianId is required");
        this.locationId = Objects.requireNonNull(locationId, "locationId is required");
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public Long getLocationId() {
        return locationId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TechnicianServiceAreaId that)) {
            return false;
        }
        return Objects.equals(technicianId, that.technicianId)
                && Objects.equals(locationId, that.locationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(technicianId, locationId);
    }
}
