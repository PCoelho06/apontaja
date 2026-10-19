package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.AppointmentReassignmentRepository;
import com.apontaja.back.customer.application.CustomerAppointmentReassignment;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/** Adaptateur du port {@link CustomerAppointmentReassignment} (fusion de clients, tranche 4b). */
@Component
class CustomerAppointmentReassignmentAdapter implements CustomerAppointmentReassignment {

    private final AppointmentReassignmentRepository repository;

    CustomerAppointmentReassignmentAdapter(AppointmentReassignmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public int reassign(UUID salonId, Collection<UUID> fromProfileIds, UUID toProfileId) {
        return repository.reassignCustomer(salonId, fromProfileIds, toProfileId);
    }
}
