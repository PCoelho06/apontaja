/**
 * Domaine {@code customer} — identité personne, "réclamable" par un compte
 * ({@code CustomerProfile}, {@code SalonCustomerLink}).
 *
 * <p><b>Position dans le graphe de dépendances</b> (§2) : dépend de {@code account} (jamais de
 * {@code salon}, {@code organization}, {@code resource} ou {@code service} — un
 * {@code CustomerProfile} est une identité partagée entre salons, pas rattachée à un salon).
 *
 * <p><b>Règle de matching {@code [DECIDED]}</b> : pas de fusion/consolidation rétroactive
 * automatique en v1. Cloisonnement strict entre identité partagée ({@code CustomerProfile} :
 * nom/email/téléphone) et données propres à la relation salon (notes internes, consentement
 * marketing, historique — sur {@code SalonCustomerLink}, jamais visibles d'un autre salon).
 */
package com.apontaja.back.customer;
