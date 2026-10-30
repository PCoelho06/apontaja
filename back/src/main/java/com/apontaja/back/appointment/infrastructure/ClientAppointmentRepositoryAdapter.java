package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.ClientAppointmentRepository;
import com.apontaja.back.appointment.domain.ClientAppointmentRow;
import com.apontaja.back.appointment.domain.ClientAppointmentRows;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ClientAppointmentRepositoryAdapter implements ClientAppointmentRepository {

    private final ClientAppointmentJpaRepository jpaRepository;

    ClientAppointmentRepositoryAdapter(ClientAppointmentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ClientAppointmentRows findForAccount(UUID accountId, boolean upcoming, Instant now, int page, int size) {
        OffsetDateTime at = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        long offset = (long) page * size;
        List<Object[]> columns = upcoming ? jpaRepository.findUpcoming(accountId, at, size, offset)
                : jpaRepository.findPast(accountId, at, size, offset);
        long total = upcoming ? jpaRepository.countUpcoming(accountId, at) : jpaRepository.countPast(accountId, at);
        return new ClientAppointmentRows(columns.stream().map(ClientAppointmentRepositoryAdapter::toRow).toList(),
                total);
    }

    @Override
    public Optional<ClientAppointmentRow> findOwned(UUID accountId, UUID appointmentId) {
        return jpaRepository.findOwned(accountId, appointmentId).stream().findFirst()
                .map(ClientAppointmentRepositoryAdapter::toRow);
    }

    private static ClientAppointmentRow toRow(Object[] c) {
        return new ClientAppointmentRow((UUID) c[0], (UUID) c[1], (String) c[2], (String) c[3], (String) c[4],
                (String) c[5], (String) c[6], (UUID) c[7], (String) c[8], (UUID) c[9], (String) c[10],
                toInstant(c[11]), toInstant(c[12]), (String) c[13], ((Number) c[14]).intValue(),
                ((Number) c[15]).intValue(), c[16] == null ? null : toInstant(c[16]),
                Boolean.TRUE.equals(c[17]));
    }

    /** Selon la configuration de Hibernate, une colonne timestamptz native arrive sous l'un de ces types. */
    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        throw new IllegalStateException("Type de date inattendu : " + value.getClass().getName());
    }
}
