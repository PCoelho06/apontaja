package com.apontaja.back.shared.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Envoi réel par SMTP (texte brut, UTF-8) via le {@link JavaMailSender} auto-configuré depuis {@code spring.mail.*}
 * (relais Brevo en production, voir {@code application-prod.yml}). Actif seulement avec le profil {@code prod} :
 * ailleurs, {@link LoggingEmailSender} journalise au lieu d'envoyer.
 *
 * <p>
 * Les retours à la ligne du sujet sont remplacés par une espace (défense contre l'injection d'en-têtes).
 */
@Component
@Profile("prod")
class SmtpEmailTransport implements EmailTransport {

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;

    SmtpEmailTransport(JavaMailSender mailSender, @Value("${apontaja.mail.from}") String from,
            @Value("${apontaja.mail.from-name:Apontaja}") String fromName) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    public void send(String to, String subject, String body) {
        mailSender.send(message -> {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject.replaceAll("[\\r\\n]+", " "));
            helper.setText(body, false);
        });
    }
}
