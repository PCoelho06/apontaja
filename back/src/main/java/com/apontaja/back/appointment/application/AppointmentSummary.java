package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;

import java.time.Instant;
import java.util.UUID;

public record AppointmentSummary(UUID appointmentId, UUID salonId, UUID customerProfileId, UUID serviceId,
        UUID resourceId, Instant startAt, Instant endAt, String status, int priceAtBookingCents,
        int durationAtBookingMinutes, Instant createdAt) {

    static AppointmentSummary of(Appointment appointment, UUID resourceId) {
        return new AppointmentSummary(appointment.getId(), appointment.getSalonId(),
                appointment.getCustomerProfileId(), appointment.getServiceId(), resourceId, appointment.getStartAt(),
                appointment.getEndAt(), appointment.getStatus().name(), appointment.getPriceAtBookingCents(),
                appointment.getDurationAtBookingMinutes(), appointment.getCreatedAt());
    }
}
