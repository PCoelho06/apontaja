package com.apontaja.back.resource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Employé ou équipement réservable dans le planning. {@code salonId} en UUID
 * brut (pas de relation JPA vers Salon). Le lien optionnel vers un
 * StaffMembership ({@code staff_membership_id}) est reporté en v2 : colonne
 * non mappée volontairement.
 *
 * <p>
 * Implémente {@link Persistable} pour la même raison que {@code Account}.
 */
@Entity
@Table(name = "resource")
public class Resource implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "salon_id", nullable = false, updatable = false)
    private UUID salonId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType type;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    private boolean isNew = true;

    protected Resource() {
        // requis par Hibernate
    }

    public Resource(UUID id, UUID salonId, String name, ResourceType type, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.salonId = Objects.requireNonNull(salonId, "salonId");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
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

    public void update(String newName, ResourceType newType) {
        this.name = Objects.requireNonNull(newName, "newName");
        this.type = Objects.requireNonNull(newType, "newType");
    }

    public void softDelete(Instant at) {
        this.deletedAt = Objects.requireNonNull(at, "at");
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getSalonId() {
        return salonId;
    }

    public ResourceType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
