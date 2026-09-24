package com.apontaja.back.service.infrastructure;

import com.apontaja.back.service.domain.SalonService;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SalonServiceJpaRepository extends JpaRepository<SalonService, UUID> {

    Optional<SalonService> findByIdAndDeletedAtIsNull(UUID id);

    List<SalonService> findBySalonIdAndDeletedAtIsNullOrderByNameAsc(UUID salonId);

    Optional<SalonService> findBySalonIdAndNameAndDeletedAtIsNull(UUID salonId, String name);
}
