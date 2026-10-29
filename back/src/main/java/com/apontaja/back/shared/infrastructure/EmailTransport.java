package com.apontaja.back.shared.infrastructure;

/**
 * Remise effective d'un email (SMTP en production, journal en dehors). Interne à {@code shared.infrastructure} : le
 * reste de l'application ne connaît que le port {@link com.apontaja.back.shared.domain.EmailSender}, implémenté par
 * {@link AsyncEmailSender} qui se charge de l'asynchronisme et du moment de l'envoi.
 */
interface EmailTransport {

    void send(String to, String subject, String body);
}
