package com.apontaja.back.account.domain;

import java.util.UUID;

/**
 * Port défini par le domaine. Génération d'ID côté application (Java), pas
 * par PostgreSQL — voir l'en-tête de apontaja-schema.sql. L'implémentation
 * (infrastructure) doit produire des UUIDv7.
 */
public interface IdGenerator {
    UUID generate();
}
