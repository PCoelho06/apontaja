package com.apontaja.back.resource.infrastructure;

import com.apontaja.back.resource.domain.Resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ResourceJpaRepository extends JpaRepository<Resource, UUID> {

    Optional<Resource> findByIdAndDeletedAtIsNull(UUID id);

    List<Resource> findBySalonIdAndDeletedAtIsNullOrderByNameAsc(UUID salonId);

    Optional<Resource> findBySalonIdAndNameAndDeletedAtIsNull(UUID salonId, String name);
}
