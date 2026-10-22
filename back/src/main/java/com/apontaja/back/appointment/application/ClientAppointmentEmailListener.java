package com.apontaja.back.appointment.application;

import com.apontaja.back.customer.application.ClientContact;
import com.apontaja.back.customer.application.ClientContactQueryService;
import com.apontaja.back.shared.domain.EmailSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Envoie l'email au client une fois la transaction validée. Une exception levée dans un écouteur AFTER_COMMIT
 * remonterait à l'appelant alors que l'action est déjà enregistrée : tout échec est donc journalisé (identifiants
 * seulement, jamais l'adresse) sans faire échouer la réservation ou l'annulation.
 */
@Component
class ClientAppointmentEmailListener {

    private static final Logger log = LoggerFactory.getLogger(ClientAppointmentEmailListener.class);

    private final ClientContactQueryService contactQueryService;
    private final EmailSender emailSender;

    ClientAppointmentEmailListener(ClientContactQueryService contactQueryService, EmailSender emailSender) {
        this.contactQueryService = contactQueryService;
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotification(ClientAppointmentNotification event) {
        try {
            Optional<ClientContact> contact = contactQueryService.find(event.accountId());
            if (contact.isEmpty()) {
                log.warn("Email {} non envoyé : compte introuvable (rendez-vous {}).", event.kind(),
                        event.appointmentId());
                return;
            }
            ClientEmailComposer.ClientEmail email = ClientEmailComposer.compose(event,
                    contact.get().firstName());
            emailSender.send(contact.get().email(), email.subject(), email.body());
        } catch (RuntimeException e) {
            log.warn("Échec de l'envoi de l'email {} (rendez-vous {}, compte {}) : {}", event.kind(),
                    event.appointmentId(), event.accountId(), e.getClass().getName());
            log.debug("Détail de l'échec d'envoi", e);
        }
    }
}
