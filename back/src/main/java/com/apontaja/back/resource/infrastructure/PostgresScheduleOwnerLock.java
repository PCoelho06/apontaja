package com.apontaja.back.resource.infrastructure;

import com.apontaja.back.resource.domain.ScheduleOwnerLock;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class PostgresScheduleOwnerLock implements ScheduleOwnerLock {

    private final EntityManager entityManager;

    PostgresScheduleOwnerLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void lock(UUID ownerId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:ownerId AS text), 1))")
                .setParameter("ownerId", ownerId.toString()).getSingleResult();
    }
}
