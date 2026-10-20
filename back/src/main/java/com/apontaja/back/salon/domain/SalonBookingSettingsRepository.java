package com.apontaja.back.salon.domain;

import java.util.Optional;
import java.util.UUID;

public interface SalonBookingSettingsRepository {

    /** Flush immédiat, comme le reste des dépôts. */
    SalonBookingSettings save(SalonBookingSettings settings);

    Optional<SalonBookingSettings> findBySalonId(UUID salonId);

    /**
     * Verrou consultatif de transaction propre aux réglages d'un salon : sérialise deux premières
     * écritures concurrentes (sinon la seconde échouerait sur la clé primaire). À appeler dans une
     * transaction.
     */
    void lockSalon(UUID salonId);
}
