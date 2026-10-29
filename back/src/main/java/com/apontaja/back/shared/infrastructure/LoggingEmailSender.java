package com.apontaja.back.shared.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Transport de dev et de tests : logue le contenu au lieu d'envoyer un vrai email. Actif partout sauf avec le profil
 * {@code prod}, où {@link SmtpEmailTransport} envoie réellement (5c). Le port {@code EmailSender} est implémenté par
 * {@link AsyncEmailSender}, qui délègue à ce transport. Le journal contient l'adresse et les liens : jamais en
 * production.
 */
@Component
@Profile("!prod")
class LoggingEmailSender implements EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("--- EMAIL (mock, non envoyé) ---\nÀ : {}\nSujet : {}\n{}\n---------------------------------", to,
                subject, body);
    }
}
