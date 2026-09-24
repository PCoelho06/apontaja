package com.apontaja.back.resource.domain;

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
 * Fermeture exceptionnelle d'un salon OU d'une ressource (jamais les deux, cf.
 * CHECK {@code ck_closure_owner_xor}), sur l'intervalle [startAt, endAt).
 * Suppression physique (pas de deleted_at dans le schéma).
 */
@Entity
@Table(name = "closure")
public class Closure implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "salon_id", updatable = false)
    private UUID salonId;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Closure() {
        // requis par Hibernate
    }

    private Closure(UUID id, UUID salonId, UUID resourceId, Instant startAt, Instant endAt, String reason,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.salonId = salonId;
        this.resourceId = resourceId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        applyPeriod(startAt, endAt, reason);
    }

    public static Closure forSalon(UUID id, UUID salonId, Instant startAt, Instant endAt, String reason,
            Instant createdAt) {
        return new Closure(id, Objects.requireNonNull(salonId, "salonId"), null, startAt, endAt, reason, createdAt);
    }

    public static Closure forResource(UUID id, UUID resourceId, Instant startAt, Instant endAt, String reason,
            Instant createdAt) {
        return new Closure(id, null, Objects.requireNonNull(resourceId, "resourceId"), startAt, endAt, reason,
                createdAt);
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

    public void reschedule(Instant newStartAt, Instant newEndAt, String newReason) {
        applyPeriod(newStartAt, newEndAt, newReason);
    }

    private void applyPeriod(Instant newStartAt, Instant newEndAt, String newReason) {
        Objects.requireNonNull(newStartAt, "startAt");
        Objects.requireNonNull(newEndAt, "endAt");
        if (!newEndAt.isAfter(newStartAt)) {
            throw new IllegalArgumentException("La fin doit être postérieure au début");
        }
        this.startAt = newStartAt;
        this.endAt = newEndAt;
        this.reason = newReason;
    }

    public UUID getSalonId() {
        return salonId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
