package com.apontaja.back.customer.application;

import java.util.UUID;

/**
 * Port implémenté par le domaine appointment : RDV non supprimés d'un client dans un salon, tous statuts, du plus
 * récent au plus ancien. {@code page} part de 0.
 */
public interface CustomerAppointmentHistory {

    CustomerHistoryPage list(UUID salonId, UUID customerProfileId, int page, int size);
}
