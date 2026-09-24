package com.apontaja.back.resource.infrastructure;

import com.apontaja.back.resource.domain.Closure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ClosureJpaRepository extends JpaRepository<Closure, UUID> {

    @Query("SELECT c FROM Closure c WHERE c.salonId = :salonId AND c.endAt > :from AND c.startAt < :to "
            + "ORDER BY c.startAt")
    List<Closure> findOverlappingBySalonId(@Param("salonId") UUID salonId, @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT c FROM Closure c WHERE c.resourceId = :resourceId AND c.endAt > :from AND c.startAt < :to "
            + "ORDER BY c.startAt")
    List<Closure> findOverlappingByResourceId(@Param("resourceId") UUID resourceId, @Param("from") Instant from,
            @Param("to") Instant to);
}
