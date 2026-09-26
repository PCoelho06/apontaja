package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.SalonCustomerLink;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SalonCustomerLinkJpaRepository extends JpaRepository<SalonCustomerLink, UUID> {

    Optional<SalonCustomerLink> findBySalonIdAndCustomerProfileIdAndDeletedAtIsNull(UUID salonId,
            UUID customerProfileId);

    List<SalonCustomerLink> findBySalonIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID salonId);
}
