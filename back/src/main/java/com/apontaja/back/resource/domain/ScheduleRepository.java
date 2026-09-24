package com.apontaja.back.resource.domain;

import java.util.List;
import java.util.UUID;

public interface ScheduleRepository {

    /** Flush immédiat. */
    List<Schedule> saveAll(List<Schedule> schedules);

    /** Plages du salon lui-même (pas celles de ses ressources). */
    List<Schedule> findBySalonId(UUID salonId);

    List<Schedule> findByResourceId(UUID resourceId);

    void deleteBySalonId(UUID salonId);

    void deleteByResourceId(UUID resourceId);
}
