/**
 * Couche {@code application} du domaine {@code organization} : services et cas d'usage
 * (création d'organisation, gestion des memberships OWNER...).
 *
 * <p>Orchestre {@code organization.domain} et peut dépendre du domaine {@code account}
 * (via ses DTO/ports applicatifs, jamais ses entités JPA) pour résoudre l'identité d'un membre.
 */
package com.apontaja.back.organization.application;
