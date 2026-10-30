package com.apontaja.back.appointment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenche l'expiration des demandes à intervalle fixe (par défaut 5 min après la fin du passage précédent, premier
 * passage 1 min après le démarrage). Désactivable par {@code apontaja.booking-requests.expiry.enabled=false}
 * (les tests). Active le support {@code @Scheduled} de l'application (c'est la seule tâche planifiée).
 */
@Component
@EnableScheduling
class RequestExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RequestExpiryScheduler.class);

    private final RequestExpiryService service;
    private final boolean enabled;

    RequestExpiryScheduler(RequestExpiryService service,
            @Value("${apontaja.booking-requests.expiry.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${apontaja.booking-requests.expiry.interval:PT5M}",
            initialDelayString = "${apontaja.booking-requests.expiry.initial-delay:PT1M}")
    void run() {
        if (!enabled) {
            return;
        }
        try {
            service.expireDueRequests();
        } catch (RuntimeException e) {
            log.warn("Passage d'expiration des demandes en échec : {}", e.getClass().getName());
            log.debug("Détail de l'échec", e);
        }
    }
}
