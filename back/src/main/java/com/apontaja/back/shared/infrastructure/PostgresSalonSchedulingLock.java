package com.apontaja.back.shared.infrastructure;

import com.apontaja.back.shared.domain.SalonSchedulingLock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Verrou consultatif PostgreSQL de transaction, clé dédiée distincte des verrous de fusion et d'horaires. */
@Component
class PostgresSalonSchedulingLock implements SalonSchedulingLock {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void lock(UUID salonId) {
        // La sous-requête évite de lire la colonne « void » renvoyée par pg_advisory_xact_lock.
        entityManager
                .createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) AS t")
                .setParameter("key", "salon-scheduling:" + salonId).getSingleResult();
    }
}
