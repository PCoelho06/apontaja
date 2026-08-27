/**
 * Couche {@code application} du domaine {@code customer} : services et cas d'usage (création de
 * client par un salon, matching sur profil déjà réclamé, gestion du carnet client...).
 *
 * <p>Orchestre {@code customer.domain} et peut dépendre du domaine {@code account}.
 *
 * <p>{@code SalonCustomerLink} référence {@code salonId} par identifiant brut (UUID), jamais par
 * relation JPA vers l'entité {@code Salon} du domaine {@code salon} — conforme à la position de
 * {@code customer} dans le graphe de dépendances, qui ne dépend que de {@code account}.
 */
package com.apontaja.back.customer.application;
