package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.salon.domain.Salon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SalonJpaRepository extends JpaRepository<Salon, UUID> {

    Optional<Salon> findByIdAndDeletedAtIsNull(UUID id);

    List<Salon> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);

    List<Salon> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
