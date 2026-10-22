package com.apontaja.back.appointment.application;

import java.util.UUID;

/**
 * Réservation en ligne par un compte connecté. {@code resourceId} nul = « sans préférence » ; {@code startAt} :
 * ISO 8601 avec offset ; {@code phone} optionnel (utilisé seulement si un profil doit être créé).
 */
public record OnlineBookingCommand(UUID accountId, UUID salonId, UUID serviceId, UUID resourceId, String startAt,
        String phone, boolean shareContactConsent) {
}
