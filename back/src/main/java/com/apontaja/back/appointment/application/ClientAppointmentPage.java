package com.apontaja.back.appointment.application;

import java.util.List;

public record ClientAppointmentPage(List<ClientAppointment> content, int page, int size, long totalElements,
        int totalPages) {
}
