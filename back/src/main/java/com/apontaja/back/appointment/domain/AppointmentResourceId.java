package com.apontaja.back.appointment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AppointmentResourceId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    protected AppointmentResourceId() {
        // requis par Hibernate
    }

    public AppointmentResourceId(UUID appointmentId, UUID resourceId) {
        this.appointmentId = Objects.requireNonNull(appointmentId, "appointmentId");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AppointmentResourceId that)) {
            return false;
        }
        return Objects.equals(appointmentId, that.appointmentId) && Objects.equals(resourceId, that.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appointmentId, resourceId);
    }
}
