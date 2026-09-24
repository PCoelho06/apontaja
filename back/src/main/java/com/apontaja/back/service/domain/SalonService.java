package com.apontaja.back.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Prestation proposée par un salon (table {@code service}). Nommée
 * {@code SalonService} pour ne pas entrer en collision avec l'annotation
 * Spring {@code @Service} dans la couche application.
 *
 * <p>
 * Implémente {@link Persistable} pour la même raison que {@code Account}.
 */
@Entity
@Table(name = "service")
public class SalonService implements Persistable<UUID> {

    private static final int MAX_DURATION_MINUTES = 1440;

    @Id
    private UUID id;

    @Column(name = "salon_id", nullable = false, updatable = false)
    private UUID salonId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "default_duration_minutes", nullable = false)
    private int defaultDurationMinutes;

    @Column(name = "default_price_cents", nullable = false)
    private int defaultPriceCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    private boolean isNew = true;

    protected SalonService() {
        // requis par Hibernate
    }

    public SalonService(UUID id, UUID salonId, String name, String description, int defaultDurationMinutes,
            int defaultPriceCents, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.salonId = Objects.requireNonNull(salonId, "salonId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        applyDetails(name, description, defaultDurationMinutes, defaultPriceCents);
    }

    @Override
    public UUID getId() {
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

    public void update(String newName, String newDescription, int newDurationMinutes, int newPriceCents) {
        applyDetails(newName, newDescription, newDurationMinutes, newPriceCents);
    }

    public void softDelete(Instant at) {
        this.deletedAt = Objects.requireNonNull(at, "at");
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void applyDetails(String newName, String newDescription, int durationMinutes, int priceCents) {
        if (durationMinutes < 1 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("Durée invalide : " + durationMinutes);
        }
        if (priceCents < 0) {
            throw new IllegalArgumentException("Prix invalide : " + priceCents);
        }
        this.name = Objects.requireNonNull(newName, "name");
        this.description = newDescription;
        this.defaultDurationMinutes = durationMinutes;
        this.defaultPriceCents = priceCents;
    }

    public UUID getSalonId() {
        return salonId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDefaultDurationMinutes() {
        return defaultDurationMinutes;
    }

    public int getDefaultPriceCents() {
        return defaultPriceCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
