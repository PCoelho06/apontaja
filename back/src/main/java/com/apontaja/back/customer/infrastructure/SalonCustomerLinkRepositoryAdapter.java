package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class SalonCustomerLinkRepositoryAdapter implements SalonCustomerLinkRepository {

    private final SalonCustomerLinkJpaRepository jpaRepository;

    SalonCustomerLinkRepositoryAdapter(SalonCustomerLinkJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SalonCustomerLink save(SalonCustomerLink link) {
        return jpaRepository.saveAndFlush(link);
    }

    @Override
    public Optional<SalonCustomerLink> findAliveBySalonIdAndCustomerProfileId(UUID salonId, UUID customerProfileId) {
        return jpaRepository.findBySalonIdAndCustomerProfileIdAndDeletedAtIsNull(salonId, customerProfileId);
    }

    @Override
    public List<SalonCustomerLink> findAliveBySalonId(UUID salonId) {
        return jpaRepository.findBySalonIdAndDeletedAtIsNullOrderByCreatedAtAsc(salonId);
    }
}
