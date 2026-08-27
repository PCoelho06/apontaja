/**
 * Couche {@code web} du domaine {@code account} : controllers REST et DTO request/response.
 *
 * <p>Ne dépend que de {@code account.application} (jamais directement de {@code account.domain}
 * ni de {@code account.infrastructure}). N'expose jamais les entités JPA du domaine — toute
 * frontière HTTP passe par un DTO dédié.
 */
package com.apontaja.back.account.web;
