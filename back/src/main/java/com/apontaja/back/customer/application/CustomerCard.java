package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.SalonCustomerLink;

import java.time.Instant;
import java.util.UUID;

/** Fiche client vue par un salon : identité, notes, consentement marketing et dates de visite. */
public record CustomerCard(UUID customerProfileId, UUID salonCustomerLinkId, UUID salonId, String firstName,
        String lastName, String email, String phone, String internalNotes, boolean marketingConsent,
        Instant marketingConsentAt, Instant marketingConsentRevokedAt, boolean anonymized, Instant createdAt,
        Instant lastVisitAt, Instant nextVisitAt) {

    static CustomerCard of(SalonCustomerLink link, CustomerProfile profile, CustomerVisits visits) {
        CustomerVisits safeVisits = visits == null ? CustomerVisits.NONE : visits;
        return new CustomerCard(profile.getId(), link.getId(), link.getSalonId(), profile.getFirstName(),
                profile.getLastName(), profile.getEmail(), profile.getPhone(), link.getInternalNotes(),
                link.hasMarketingConsent(), link.getMarketingConsentAt(), link.getMarketingConsentRevokedAt(),
                profile.isAnonymized(), profile.getCreatedAt(), safeVisits.lastVisitAt(),
                safeVisits.nextVisitAt());
    }

    /** Client qui vient d'être créé : ni consentement, ni visite. */
    public static CustomerCard ofNew(CustomerSummary summary) {
        return new CustomerCard(summary.customerProfileId(), summary.salonCustomerLinkId(), summary.salonId(),
                summary.firstName(), summary.lastName(), summary.email(), summary.phone(), summary.internalNotes(),
                false, null, null, false, summary.createdAt(), null, null);
    }
}
