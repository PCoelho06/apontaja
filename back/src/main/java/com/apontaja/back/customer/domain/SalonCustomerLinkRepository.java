package com.apontaja.back.customer.domain;

import java.util.Optional;
import java.util.UUID;

public interface SalonCustomerLinkRepository {

    /** Flush immédiat, cohérent avec le reste du repo. */
    SalonCustomerLink save(SalonCustomerLink link);

    Optional<SalonCustomerLink> findAliveBySalonIdAndCustomerProfileId(UUID salonId, UUID customerProfileId);
}
