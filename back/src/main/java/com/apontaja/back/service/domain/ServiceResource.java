package com.apontaja.back.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Une ressource capable de réaliser un service, avec surcharges optionnelles
 * de prix/durée. Pas de created_at ni de soft-delete dans le schéma : une
 * association se supprime physiquement.
 *
 * <p>
 * Implémente {@link Persistable} (identifiant composite assigné côté
 * application, même raison que les autres entités).
 */
@Entity
@Table(name = "service_resource")
public class ServiceResource implements Persistable<ServiceResourceId> {

    @EmbeddedId
    private ServiceResourceId id;

    @Column(name = "override_price_cents")
    private Integer overridePriceCents;

    @Column(name = "override_duration_minutes")
    private Integer overrideDurationMinutes;

    @Transient
    private boolean isNew = true;

    protected ServiceResource() {
        // requis par Hibernate
    }

    public ServiceResource(UUID serviceId, UUID resourceId, Integer overridePriceCents,
            Integer overrideDurationMinutes) {
        this.id = new ServiceResourceId(serviceId, resourceId);
        changeOverrides(overridePriceCents, overrideDurationMinutes);
    }

    @Override
    public ServiceResourceId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public void changeOverrides(Integer newPriceCents, Integer newDurationMinutes) {
        if (newPriceCents != null && newPriceCents < 0) {
            throw new IllegalArgumentException("Prix invalide : " + newPriceCents);
        }
        if (newDurationMinutes != null && (newDurationMinutes < 1 || newDurationMinutes > 1440)) {
            throw new IllegalArgumentException("Durée invalide : " + newDurationMinutes);
        }
        this.overridePriceCents = newPriceCents;
        this.overrideDurationMinutes = newDurationMinutes;
    }

    public UUID getServiceId() {
        return id.getServiceId();
    }

    public UUID getResourceId() {
        return id.getResourceId();
    }

    public Integer getOverridePriceCents() {
        return overridePriceCents;
    }

    public Integer getOverrideDurationMinutes() {
        return overrideDurationMinutes;
    }
}
