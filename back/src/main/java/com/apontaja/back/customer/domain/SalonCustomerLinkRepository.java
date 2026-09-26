package com.apontaja.back.customer.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalonCustomerLinkRepository {

    /** Flush immédiat, cohérent avec le reste du repo. */
    SalonCustomerLink save(SalonCustomerLink link);

    Optional<SalonCustomerLink> findAliveBySalonIdAndCustomerProfileId(UUID salonId, UUID customerProfileId);

    List<SalonCustomerLink> findAliveBySalonId(UUID salonId);
}
