package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.salon.domain.SalonBookingSettings;
import com.apontaja.back.salon.domain.SalonBookingSettingsRepository;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class SalonBookingSettingsRepositoryAdapter implements SalonBookingSettingsRepository {

    private static final String LOCK_KEY_PREFIX = "salon-booking-settings:";

    private final SalonBookingSettingsJpaRepository jpaRepository;

    SalonBookingSettingsRepositoryAdapter(SalonBookingSettingsJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SalonBookingSettings save(SalonBookingSettings settings) {
        return jpaRepository.saveAndFlush(settings);
    }

    @Override
    public Optional<SalonBookingSettings> findBySalonId(UUID salonId) {
        return jpaRepository.findById(salonId);
    }

    @Override
    public void lockSalon(UUID salonId) {
        jpaRepository.lock(LOCK_KEY_PREFIX + salonId);
    }
}
