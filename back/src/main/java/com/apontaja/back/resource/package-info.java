/**
 * Domaine {@code resource} — employé ou équipement réservable dans le planning
 * ({@code Resource}, {@code Schedule}, {@code Closure}).
 *
 * <p><b>Position dans le graphe de dépendances</b> (§2) : dépend de {@code salon}.
 *
 * <p><b>Règle de scoping explicite</b> : une {@code Schedule}/{@code Closure} de type ressource
 * ({@code resourceId} renseigné, {@code salonId} null) est toujours résolue via
 * {@code Resource → Salon} pour déterminer son périmètre réel — jamais utilisée seule pour un
 * contrôle d'autorisation par salon.
 */
package com.apontaja.back.resource;
