package com.apontaja.back.shared.domain;

import java.util.UUID;

/**
 * Génération d'ID côté application (Java), pas par PostgreSQL — voir l'en-tête
 * de apontaja-schema.sql. L'implémentation (infrastructure) doit produire des
 * UUIDv7.
 *
 * <p>
 * Déplacé depuis {@code account.domain} en Phase 2 : plusieurs domaines
 * (organization, salon, et bientôt resource/service/appointment) en ont besoin
 * — {@code account} n'est pas censé être une dépendance technique universelle
 * malgré sa position de racine du graphe métier.
 */
public interface IdGenerator {
    UUID generate();
}
