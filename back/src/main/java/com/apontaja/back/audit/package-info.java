/**
 * Domaine {@code audit} — traçabilité ({@code AuditLog}).
 *
 * <p><b>Position dans le graphe de dépendances</b> (§2) : utilitaire transverse que tous les
 * domaines peuvent appeler en écriture, sans dépendre de personne lui-même. Autrement dit :
 * beaucoup de domaines dépendent de {@code audit}, mais {@code audit} ne dépend d'aucun d'eux.
 *
 * <p><b>Règle PII</b> : jamais de copie automatique de données personnelles/sensibles dans
 * {@code before}/{@code after} — whitelist explicite de champs traçables uniquement.
 */
package com.apontaja.back.audit;
