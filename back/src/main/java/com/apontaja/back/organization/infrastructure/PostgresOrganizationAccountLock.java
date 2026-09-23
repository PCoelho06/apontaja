package com.apontaja.back.organization.infrastructure;

import com.apontaja.back.organization.domain.OrganizationAccountLock;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class PostgresOrganizationAccountLock implements OrganizationAccountLock {

    private final EntityManager entityManager;

    PostgresOrganizationAccountLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void lock(UUID accountId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:accountId AS text), 0))")
                .setParameter("accountId", accountId.toString()).getSingleResult();
    }
}
