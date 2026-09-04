package com.apontaja.back.salon.application;

import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class SalonQueryService {

    private final SalonRepository salonRepository;

    SalonQueryService(SalonRepository salonRepository) {
        this.salonRepository = salonRepository;
    }

    public Optional<SalonSummary> findAliveById(UUID salonId) {
        return salonRepository.findAliveById(salonId).map(SalonQueryService::toSummary);
    }

    private static SalonSummary toSummary(Salon salon) {
        return new SalonSummary(salon.getId(), salon.getOrganizationId(), salon.getName(), salon.getAddress(),
                salon.getPostalCode(), salon.getCity(), salon.getCountry(), salon.getPhone(), salon.getTimezone());
    }
}
