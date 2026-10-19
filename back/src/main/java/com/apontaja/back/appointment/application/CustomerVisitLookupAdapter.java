package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.AppointmentVisitRepository;
import com.apontaja.back.appointment.domain.CustomerVisitDate;
import com.apontaja.back.customer.application.CustomerVisitLookup;
import com.apontaja.back.customer.application.CustomerVisits;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Implémente le port de customer : les dates de visite sont dérivées des RDV du salon. */
@Component
class CustomerVisitLookupAdapter implements CustomerVisitLookup {

    private final AppointmentVisitRepository visitRepository;

    CustomerVisitLookupAdapter(AppointmentVisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    @Override
    public Map<UUID, CustomerVisits> findVisits(UUID salonId, Collection<UUID> customerProfileIds, Instant now) {
        Map<UUID, Instant> last = visitRepository.findLastCompletedStarts(salonId, customerProfileIds).stream()
                .collect(Collectors.toMap(CustomerVisitDate::customerProfileId, CustomerVisitDate::startAt));
        Map<UUID, Instant> next = visitRepository.findNextActiveStarts(salonId, customerProfileIds, now).stream()
                .collect(Collectors.toMap(CustomerVisitDate::customerProfileId, CustomerVisitDate::startAt));

        Set<UUID> ids = new HashSet<>(last.keySet());
        ids.addAll(next.keySet());
        Map<UUID, CustomerVisits> result = new HashMap<>();
        for (UUID id : ids) {
            result.put(id, new CustomerVisits(last.get(id), next.get(id)));
        }
        return result;
    }
}
