package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.RequestExpiryPolicy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lecture seule, SQL natif (jointure sur {@code customer_profile.account_id}, tables d'autres domaines lues pour
 * leurs noms). Colonnes renvoyées, dans l'ordre : 0 id RDV, 1 id salon, 2 nom salon, 3 adresse, 4 code postal,
 * 5 ville, 6 fuseau, 7 id prestation, 8 nom prestation, 9 id ressource, 10 nom ressource, 11 début, 12 fin,
 * 13 statut, 14 prix, 15 durée, 16 annulé le, 17 expirée (annulée par le système faute de réponse du salon).
 */
interface ClientAppointmentJpaRepository extends Repository<Appointment, UUID> {

    String SELECT = "SELECT a.id, s.id, s.name, s.address, s.postal_code, s.city, s.timezone, sv.id, sv.name, "
            + "r.id, r.name, a.start_at, a.end_at, a.status, a.price_at_booking_cents, "
            + "a.duration_at_booking_minutes, a.cancelled_at, CASE WHEN a.status = 'CANCELLED' "
            + "AND a.cancelled_by IS NULL AND a.cancellation_reason = '" + RequestExpiryPolicy.EXPIRY_REASON
            + "' THEN true ELSE false END ";
    String FROM = "FROM appointment a JOIN customer_profile p ON p.id = a.customer_profile_id "
            + "JOIN salon s ON s.id = a.salon_id JOIN service sv ON sv.id = a.service_id "
            + "JOIN appointment_resource ar ON ar.appointment_id = a.id JOIN resource r ON r.id = ar.resource_id ";
    String OWNED = "WHERE p.account_id = :accountId AND p.deleted_at IS NULL AND a.deleted_at IS NULL ";
    String UPCOMING = "AND a.status IN ('SCHEDULED', 'CONFIRMED') AND a.end_at > :now ";
    String PAST = "AND NOT (a.status IN ('SCHEDULED', 'CONFIRMED') AND a.end_at > :now) ";

    @Query(value = SELECT + FROM + OWNED + UPCOMING + "ORDER BY a.start_at ASC, a.id ASC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Object[]> findUpcoming(@Param("accountId") UUID accountId, @Param("now") OffsetDateTime now,
            @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = SELECT + FROM + OWNED + PAST + "ORDER BY a.start_at DESC, a.id DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Object[]> findPast(@Param("accountId") UUID accountId, @Param("now") OffsetDateTime now,
            @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = "SELECT count(*) " + FROM + OWNED + UPCOMING, nativeQuery = true)
    long countUpcoming(@Param("accountId") UUID accountId, @Param("now") OffsetDateTime now);

    @Query(value = "SELECT count(*) " + FROM + OWNED + PAST, nativeQuery = true)
    long countPast(@Param("accountId") UUID accountId, @Param("now") OffsetDateTime now);

    @Query(value = SELECT + FROM + OWNED + "AND a.id = :appointmentId", nativeQuery = true)
    List<Object[]> findOwned(@Param("accountId") UUID accountId, @Param("appointmentId") UUID appointmentId);
}
