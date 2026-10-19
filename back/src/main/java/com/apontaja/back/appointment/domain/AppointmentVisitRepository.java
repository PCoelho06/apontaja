package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Lectures du carnet client dérivées des RDV : dates de visite et historique. Toutes filtrées par salon. */
public interface AppointmentVisitRepository {

    /** Pour chaque client ayant au moins un RDV {@code COMPLETED} : le début du plus récent. */
    List<CustomerVisitDate> findLastCompletedStarts(UUID salonId, Collection<UUID> customerProfileIds);

    /** Pour chaque client ayant un RDV {@code SCHEDULED}/{@code CONFIRMED} après {@code now} : le plus proche. */
    List<CustomerVisitDate> findNextActiveStarts(UUID salonId, Collection<UUID> customerProfileIds, Instant now);

    /** RDV non supprimés du client, tous statuts, du plus récent au plus ancien. {@code page} part de 0. */
    HistoryPage findHistory(UUID salonId, UUID customerProfileId, int page, int size);

    record HistoryPage(List<AppointmentHistoryRow> rows, long totalElements) {
    }
}
