package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.SalonCustomerLink;

import java.time.Instant;
import java.util.UUID;

/** {@code createdAt} = date de rattachement à CE salon (SalonCustomerLink), pas date du profil. */
public record CustomerSummary(UUID customerProfileId, UUID salonCustomerLinkId, UUID salonId, String firstName,
        String lastName, String email, String phone, String internalNotes, Instant createdAt) {

    static CustomerSummary of(SalonCustomerLink link, CustomerProfile profile) {
        return new CustomerSummary(profile.getId(), link.getId(), link.getSalonId(), profile.getFirstName(),
                profile.getLastName(), profile.getEmail(), profile.getPhone(), link.getInternalNotes(),
                link.getCreatedAt());
    }
}
