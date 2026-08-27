/**
 * Couche {@code application} du domaine {@code appointment} : services et cas d'usage (prise de
 * RDV avec vérification créneau/horaires/disponibilité, revalidation complète à la mise à jour,
 * snapshot prix/durée, annulation...).
 *
 * <p>Orchestre {@code appointment.domain} et peut dépendre des domaines {@code salon},
 * {@code resource}, {@code service}, {@code customer}.
 */
package com.apontaja.back.appointment.application;
