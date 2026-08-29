package com.apontaja.back.account.domain;

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
 * Consentement plateforme (CGU, confidentialité, marketing global) — distinct
 * du consentement marketing par salon.
 *
 * <p>
 * Implémente {@link Persistable} pour la même raison que {@link Account}
 * (ID UUIDv7 assigné côté application).
 */
@Entity
@Table(name = "consent_record")
public class ConsentRecord implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentType type;

    @Column(nullable = false)
    private String version;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Transient
    private boolean isNew = true;

    protected ConsentRecord() {
        // requis par Hibernate
    }

    public ConsentRecord(UUID id, UUID accountId, ConsentType type, String version, Instant acceptedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.type = Objects.requireNonNull(type, "type");
        this.version = Objects.requireNonNull(version, "version");
        this.acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
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

    public UUID getAccountId() {
        return accountId;
    }

    public ConsentType getType() {
        return type;
    }

    public String getVersion() {
        return version;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
