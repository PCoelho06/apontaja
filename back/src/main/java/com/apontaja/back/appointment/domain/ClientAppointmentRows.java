package com.apontaja.back.appointment.domain;

import java.util.List;

/** Une page de RDV d'un client et le nombre total de RDV correspondants. */
public record ClientAppointmentRows(List<ClientAppointmentRow> rows, long totalElements) {
}
