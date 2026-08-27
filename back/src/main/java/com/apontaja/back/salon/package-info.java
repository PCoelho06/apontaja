/**
 * Domaine {@code salon} — le salon lui-même ({@code Salon}) et le rattachement du staff
 * ({@code StaffMembership}).
 *
 * <p><b>Position dans le graphe de dépendances</b> (§2) : dépend de {@code organization}.
 * Toute logique métier (recherche, modification, suppression) est systématiquement filtrée par
 * {@code salonId}, jamais par organisation directement.
 */
package com.apontaja.back.salon;
