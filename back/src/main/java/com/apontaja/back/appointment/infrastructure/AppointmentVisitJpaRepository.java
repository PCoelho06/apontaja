package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentHistoryRow;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.CustomerVisitDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface AppointmentVisitJpaRepository extends JpaRepository<Appointment, UUID> {

    String HISTORY_WHERE = " FROM Appointment a WHERE a.salonId = :salonId "
            + "AND a.customerProfileId = :customerProfileId AND a.deletedAt IS NULL";

    @Query("SELECT new com.apontaja.back.appointment.domain.CustomerVisitDate(a.customerProfileId, MAX(a.startAt)) "
            + "FROM Appointment a WHERE a.salonId = :salonId AND a.customerProfileId IN :customerProfileIds "
            + "AND a.deletedAt IS NULL AND a.status = :status GROUP BY a.customerProfileId")
    List<CustomerVisitDate> findLastStartByStatus(@Param("salonId") UUID salonId,
            @Param("customerProfileIds") Collection<UUID> customerProfileIds,
            @Param("status") AppointmentStatus status);

    @Query("SELECT new com.apontaja.back.appointment.domain.CustomerVisitDate(a.customerProfileId, MIN(a.startAt)) "
            + "FROM Appointment a WHERE a.salonId = :salonId AND a.customerProfileId IN :customerProfileIds "
            + "AND a.deletedAt IS NULL AND a.status IN :statuses AND a.startAt > :now "
            + "GROUP BY a.customerProfileId")
    List<CustomerVisitDate> findNextStartByStatuses(@Param("salonId") UUID salonId,
            @Param("customerProfileIds") Collection<UUID> customerProfileIds,
            @Param("statuses") Collection<AppointmentStatus> statuses, @Param("now") Instant now);

    @Query(value = "SELECT new com.apontaja.back.appointment.domain.AppointmentHistoryRow(a.id, a.startAt, "
            + "a.endAt, a.status, a.serviceId, a.priceAtBookingCents, a.durationAtBookingMinutes)" + HISTORY_WHERE
            + " ORDER BY a.startAt DESC, a.id", countQuery = "SELECT COUNT(a)" + HISTORY_WHERE)
    Page<AppointmentHistoryRow> findHistory(@Param("salonId") UUID salonId,
            @Param("customerProfileId") UUID customerProfileId, Pageable pageable);
}
