package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.AppointmentExpiryRepository;
import com.apontaja.back.appointment.domain.PendingRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Expiration des demandes de réservation en ligne non traitées (5e-1). Un passage traite au plus
 * {@value #BATCH_SIZE} demandes, de la plus ancienne à la plus récente ; le reste attend le passage suivant.
 * Chaque demande a sa transaction : l'échec de l'une (journalisé, identifiant seulement) n'arrête pas les autres.
 * Idempotent, donc sans danger si plusieurs instances tournent (la relecture sous verrou décide).
 */
@Service
class RequestExpiryService {

    static final int BATCH_SIZE = 200;

    private static final Logger log = LoggerFactory.getLogger(RequestExpiryService.class);

    private final AppointmentExpiryRepository expiryRepository;
    private final RequestExpiryProcessor processor;
    private final Clock clock;

    RequestExpiryService(AppointmentExpiryRepository expiryRepository, RequestExpiryProcessor processor,
            Clock clock) {
        this.expiryRepository = expiryRepository;
        this.processor = processor;
        this.clock = clock;
    }

    int expireDueRequests() {
        return expireDueRequests(clock.instant());
    }

    /** Nombre de demandes expirées. L'instant est un paramètre pour les tests. */
    int expireDueRequests(Instant now) {
        List<PendingRequest> due = expiryRepository.findDue(now, BATCH_SIZE);
        int expired = 0;
        for (PendingRequest request : due) {
            try {
                if (processor.expire(request.appointmentId(), request.salonId(), now)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Expiration impossible (rendez-vous {}, salon {}) : {}", request.appointmentId(),
                        request.salonId(), e.getClass().getName());
                log.debug("Détail de l'échec d'expiration", e);
            }
        }
        if (expired > 0) {
            log.info("{} demande(s) de réservation expirée(s).", expired);
        }
        return expired;
    }
}
