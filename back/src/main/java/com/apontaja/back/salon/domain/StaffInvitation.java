package com.apontaja.back.salon.domain;

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
 * Invitation d'un compte (existant ou non) à rejoindre le staff d'un salon avec
 * un rôle donné. Même mécanique de token à usage unique que
 * {@code AccountToken} (compte), mais table distincte :
 * {@code account_token.account_id} est NOT NULL, incompatible avec une
 * invitation dont la cible n'a pas encore de compte.
 *
 * <p>
 * Implémente {@link Persistable} pour la même raison que les autres entités du
 * projet.
 */
@Entity
@Table(name = "staff_invitation")
public class StaffInvitation implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "salon_id", nullable = false)
    private UUID salonId;

    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected StaffInvitation() {
        // requis par Hibernate
    }

    public StaffInvitation(UUID id, UUID salonId, String email, StaffRole role, UUID invitedBy, String tokenHash,
            Instant expiresAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.salonId = Objects.requireNonNull(salonId, "salonId");
        this.email = Objects.requireNonNull(email, "email");
        this.role = Objects.requireNonNull(role, "role");
        this.invitedBy = invitedBy;
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
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

    public boolean isAccepted() {
        return acceptedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsable(Instant now) {
        return !isAccepted() && !isRevoked() && !isExpired(now);
    }

    public void markAccepted(Instant at) {
        this.acceptedAt = Objects.requireNonNull(at, "at");
    }

    public void revoke(Instant at) {
        this.revokedAt = Objects.requireNonNull(at, "at");
    }

    public UUID getSalonId() {
        return salonId;
    }

    public String getEmail() {
        return email;
    }

    public StaffRole getRole() {
        return role;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
