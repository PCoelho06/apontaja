package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.CustomerMergeLock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Verrou consultatif PostgreSQL de transaction (même principe que pour les horaires). */
@Repository
class CustomerMergeLockAdapter implements CustomerMergeLock {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void lockSalon(UUID salonId) {
        // La sous-requête évite de lire la colonne « void » renvoyée par pg_advisory_xact_lock.
        entityManager
                .createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) AS t")
                .setParameter("key", "customer-merge:" + salonId).getSingleResult();
    }
}
