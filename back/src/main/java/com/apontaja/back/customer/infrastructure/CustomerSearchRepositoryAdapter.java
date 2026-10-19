package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.CustomerSearchRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.UUID;

@Repository
class CustomerSearchRepositoryAdapter implements CustomerSearchRepository {

    private final CustomerSearchJpaRepository jpaRepository;

    CustomerSearchRepositoryAdapter(CustomerSearchJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public LinkPage searchAliveLinks(UUID salonId, String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<SalonCustomerLink> result;
        if (query == null || query.isBlank()) {
            result = jpaRepository.findAliveBySalon(salonId, pageable);
        } else {
            String lowered = query.trim().toLowerCase(Locale.ROOT);
            String pattern = likePattern(lowered);
            // Un téléphone peut être saisi avec espaces, points ou tirets : on compare sans ces séparateurs.
            String phoneDigits = lowered.replaceAll("[\\s.\\-]", "");
            String phonePattern = phoneDigits.isEmpty() ? pattern : likePattern(phoneDigits);
            result = jpaRepository.searchAliveBySalon(salonId, pattern, phonePattern, pageable);
        }
        return new LinkPage(result.getContent(), result.getTotalElements());
    }

    @Override
    public boolean existsAliveLinkInOtherSalon(UUID customerProfileId, UUID salonId) {
        return jpaRepository.existsByCustomerProfileIdAndSalonIdNotAndDeletedAtIsNull(customerProfileId, salonId);
    }

    private static String likePattern(String lowered) {
        String escaped = lowered.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
