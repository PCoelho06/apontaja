package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.AppointmentVisitRepository;
import com.apontaja.back.customer.application.CustomerAppointmentHistory;
import com.apontaja.back.customer.application.CustomerHistoryItem;
import com.apontaja.back.customer.application.CustomerHistoryPage;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Implémente le port de customer : historique des RDV d'un client dans un salon. */
@Component
class CustomerAppointmentHistoryAdapter implements CustomerAppointmentHistory {

    private final AppointmentVisitRepository visitRepository;

    CustomerAppointmentHistoryAdapter(AppointmentVisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    @Override
    public CustomerHistoryPage list(UUID salonId, UUID customerProfileId, int page, int size) {
        AppointmentVisitRepository.HistoryPage result = visitRepository.findHistory(salonId, customerProfileId,
                page, size);
        List<CustomerHistoryItem> items = result.rows().stream()
                .map(row -> new CustomerHistoryItem(row.appointmentId(), row.startAt(), row.endAt(),
                        row.status().name(), row.serviceId(), row.priceAtBookingCents(),
                        row.durationAtBookingMinutes()))
                .toList();
        return new CustomerHistoryPage(items, page, size, result.totalElements());
    }
}
