package com.apontaja.back.shared.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.apontaja.back.shared.domain.EmailSender;

/**
 * Mock v1 : logue le contenu au lieu d'envoyer un vrai email. À remplacer par
 * un vrai provider (SMTP/Postmark/etc.) quand l'hébergement de production sera
 * choisi (§6 [OPEN] du contexte).
 */
@Component
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("--- EMAIL (mock, non envoyé) ---\nÀ : {}\nSujet : {}\n{}\n---------------------------------", to,
                subject, body);
    }
}
