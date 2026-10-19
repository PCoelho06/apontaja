package com.apontaja.back.customer.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Port implémenté par le domaine appointment (le graphe ArchUnit autorise appointment à dépendre de
 * customer.application, pas l'inverse).
 *
 * <p>
 * Dernière visite : début du dernier RDV {@code COMPLETED} du salon. Prochaine visite : début du premier RDV
 * {@code SCHEDULED} ou {@code CONFIRMED} du salon strictement postérieur à {@code now}. Un client absent du
 * résultat n'a aucune visite.
 */
public interface CustomerVisitLookup {

    Map<UUID, CustomerVisits> findVisits(UUID salonId, Collection<UUID> customerProfileIds, Instant now);
}
