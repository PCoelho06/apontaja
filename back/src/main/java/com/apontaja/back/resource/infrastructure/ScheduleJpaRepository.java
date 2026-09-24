package com.apontaja.back.resource.infrastructure;

import com.apontaja.back.resource.domain.Schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface ScheduleJpaRepository extends JpaRepository<Schedule, UUID> {

    List<Schedule> findBySalonId(UUID salonId);

    List<Schedule> findByResourceId(UUID resourceId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Schedule s WHERE s.salonId = :salonId")
    int deleteAllBySalonId(@Param("salonId") UUID salonId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Schedule s WHERE s.resourceId = :resourceId")
    int deleteAllByResourceId(@Param("resourceId") UUID resourceId);
}
