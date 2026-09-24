package com.apontaja.back.resource.infrastructure;

import com.apontaja.back.resource.domain.Schedule;
import com.apontaja.back.resource.domain.ScheduleRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class ScheduleRepositoryAdapter implements ScheduleRepository {

    private final ScheduleJpaRepository jpaRepository;

    ScheduleRepositoryAdapter(ScheduleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Schedule> saveAll(List<Schedule> schedules) {
        return jpaRepository.saveAllAndFlush(schedules);
    }

    @Override
    public List<Schedule> findBySalonId(UUID salonId) {
        return jpaRepository.findBySalonId(salonId);
    }

    @Override
    public List<Schedule> findByResourceId(UUID resourceId) {
        return jpaRepository.findByResourceId(resourceId);
    }

    @Override
    public void deleteBySalonId(UUID salonId) {
        jpaRepository.deleteAllBySalonId(salonId);
    }

    @Override
    public void deleteByResourceId(UUID resourceId) {
        jpaRepository.deleteAllByResourceId(resourceId);
    }
}
