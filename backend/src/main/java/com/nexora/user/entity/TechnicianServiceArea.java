package com.nexora.user.entity;

import java.util.Objects;

import com.nexora.location.entity.Location;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "technician_service_areas", schema = "dbo")
public class TechnicianServiceArea {

    @EmbeddedId
    private TechnicianServiceAreaId id;

    @MapsId("technicianId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private TechnicianProfile technician;

    @MapsId("locationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    protected TechnicianServiceArea() {
    }

    public TechnicianServiceArea(TechnicianProfile technician, Location location) {
        this.technician = Objects.requireNonNull(technician, "technician is required");
        this.location = Objects.requireNonNull(location, "location is required");
        this.id = new TechnicianServiceAreaId(
                Objects.requireNonNull(
                        technician.getTechnicianId(),
                        "technician must be persisted before assigning a service area"),
                Objects.requireNonNull(
                        location.getId(),
                        "location must be persisted before service-area assignment"));
    }

    public TechnicianServiceAreaId getId() {
        return id;
    }

    public TechnicianProfile getTechnician() {
        return technician;
    }

    public Location getLocation() {
        return location;
    }
}
