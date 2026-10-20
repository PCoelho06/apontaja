package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.salon.domain.SalonBookingSettings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface SalonBookingSettingsJpaRepository extends JpaRepository<SalonBookingSettings, UUID> {

    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) AS t",
            nativeQuery = true)
    Integer lock(@Param("key") String key);
}
