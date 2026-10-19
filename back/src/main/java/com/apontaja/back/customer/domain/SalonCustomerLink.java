package com.apontaja.back.customer.domain;

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
 * Carnet client : rattache un {@link CustomerProfile} à un salon, strictement
 * propre à ce salon (jamais visible d'un autre, cf. règle de matching §4 du
 * contexte). Le consentement marketing est mappé ; les dates de visite sont
 * dérivées des RDV et ne sont pas stockées.
 *
 * <p>
 * Implémente {@link Persistable} pour la même raison que {@code Account}.
 */
@Entity
@Table(name = "salon_customer_link")
public class SalonCustomerLink implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "salon_id", nullable = false, updatable = false)
    private UUID salonId;

    @Column(name = "customer_profile_id", nullable = false, updatable = false)
    private UUID customerProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CustomerLinkSource source;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "marketing_consent_at")
    private Instant marketingConsentAt;

    @Column(name = "marketing_consent_revoked_at")
    private Instant marketingConsentRevokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    private boolean isNew = true;

    protected SalonCustomerLink() {
        // requis par Hibernate
    }

    public SalonCustomerLink(UUID id, UUID salonId, UUID customerProfileId, CustomerLinkSource source,
            String internalNotes, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.salonId = Objects.requireNonNull(salonId, "salonId");
        this.customerProfileId = Objects.requireNonNull(customerProfileId, "customerProfileId");
        this.source = Objects.requireNonNull(source, "source");
        this.internalNotes = internalNotes;
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

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getSalonId() {
        return salonId;
    }

    public UUID getCustomerProfileId() {
        return customerProfileId;
    }

    public CustomerLinkSource getSource() {
        return source;
    }

    public String getInternalNotes() {
        return internalNotes;
    }

    public Instant getMarketingConsentAt() {
        return marketingConsentAt;
    }

    public Instant getMarketingConsentRevokedAt() {
        return marketingConsentRevokedAt;
    }

    public boolean hasMarketingConsent() {
        return marketingConsentAt != null && marketingConsentRevokedAt == null;
    }

    public void updateInternalNotes(String internalNotes) {
        this.internalNotes = internalNotes;
    }

    public void grantMarketingConsent(Instant at) {
        this.marketingConsentAt = Objects.requireNonNull(at, "at");
        this.marketingConsentRevokedAt = null;
    }

    public void revokeMarketingConsent(Instant at) {
        this.marketingConsentRevokedAt = Objects.requireNonNull(at, "at");
    }

    /** Efface notes et consentement (anonymisation). */
    public void clearPersonalData() {
        this.internalNotes = null;
        this.marketingConsentAt = null;
        this.marketingConsentRevokedAt = null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
