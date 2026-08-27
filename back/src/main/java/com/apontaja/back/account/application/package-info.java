/**
 * Couche {@code application} du domaine {@code account} : services et cas d'usage
 * (inscription, changement de mot de passe, vérification d'email...).
 *
 * <p>Orchestre {@code account.domain}. Ne dépend jamais de {@code account.web} ni directement de
 * {@code account.infrastructure} (celle-ci implémente des ports/interfaces définis par
 * {@code account.domain}, injectés ici par Spring).
 */
package com.apontaja.back.account.application;
