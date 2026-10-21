package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.CustomerClaimLock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Verrou consultatif PostgreSQL de transaction (même principe que la fusion). */
@Repository
class CustomerClaimLockAdapter implements CustomerClaimLock {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void lockAccount(UUID accountId) {
        entityManager
                .createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) AS t")
                .setParameter("key", "customer-claim:" + accountId).getSingleResult();
    }
}
