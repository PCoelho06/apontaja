package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Un RDV vu par son client, avec les noms du salon, de la prestation et de la
 * ressource (lus même s'ils ont été supprimés depuis). Jamais d'identifiant de
 * profil ni de compte, ni de motif ou d'auteur d'annulation.
 */
public record ClientAppointmentRow(UUID appointmentId, UUID salonId, String salonName, String salonAddress,
                String salonPostalCode, String salonCity, String salonTimezone, UUID serviceId, String serviceName,
                UUID resourceId, String resourceName, Instant startAt, Instant endAt, String status, int priceCents,
                int durationMinutes, Instant cancelledAt, boolean expired) {

        public ClientAppointmentRow(UUID appointmentId, UUID salonId, String salonName, String salonAddress,
                        String salonPostalCode, String salonCity, String salonTimezone, UUID serviceId,
                        String serviceName, UUID resourceId, String resourceName, Instant startAt, Instant endAt,
                        String status, int priceCents, int durationMinutes, Instant cancelledAt) {
                this(appointmentId, salonId, salonName, salonAddress, salonPostalCode, salonCity, salonTimezone,
                                serviceId, serviceName, resourceId, resourceName, startAt, endAt, status, priceCents,
                                durationMinutes, cancelledAt, false);
        }
}
