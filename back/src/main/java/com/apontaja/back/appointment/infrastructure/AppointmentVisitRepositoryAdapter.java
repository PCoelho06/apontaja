package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.AppointmentHistoryRow;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.AppointmentVisitRepository;
import com.apontaja.back.appointment.domain.CustomerVisitDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
class AppointmentVisitRepositoryAdapter implements AppointmentVisitRepository {

    private static final List<AppointmentStatus> UPCOMING_STATUSES = List.of(AppointmentStatus.SCHEDULED,
            AppointmentStatus.CONFIRMED);

    private final AppointmentVisitJpaRepository jpaRepository;

    AppointmentVisitRepositoryAdapter(AppointmentVisitJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<CustomerVisitDate> findLastCompletedStarts(UUID salonId, Collection<UUID> customerProfileIds) {
        if (customerProfileIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findLastStartByStatus(salonId, customerProfileIds, AppointmentStatus.COMPLETED);
    }

    @Override
    public List<CustomerVisitDate> findNextActiveStarts(UUID salonId, Collection<UUID> customerProfileIds,
            Instant now) {
        if (customerProfileIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findNextStartByStatuses(salonId, customerProfileIds, UPCOMING_STATUSES, now);
    }

    @Override
    public HistoryPage findHistory(UUID salonId, UUID customerProfileId, int page, int size) {
        Page<AppointmentHistoryRow> result = jpaRepository.findHistory(salonId, customerProfileId,
                PageRequest.of(page, size));
        return new HistoryPage(result.getContent(), result.getTotalElements());
    }
}
