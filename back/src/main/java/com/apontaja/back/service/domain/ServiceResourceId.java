package com.apontaja.back.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ServiceResourceId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    protected ServiceResourceId() {
        // requis par Hibernate
    }

    public ServiceResourceId(UUID serviceId, UUID resourceId) {
        this.serviceId = Objects.requireNonNull(serviceId, "serviceId");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ServiceResourceId that)) {
            return false;
        }
        return Objects.equals(serviceId, that.serviceId) && Objects.equals(resourceId, that.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceId, resourceId);
    }
}
