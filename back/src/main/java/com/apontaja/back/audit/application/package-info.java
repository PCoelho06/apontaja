/**
 * Couche {@code application} du domaine {@code audit} : service d'écriture d'audit exposé aux
 * autres domaines (appelé en écriture depuis n'importe quel domaine métier), et cas d'usage de
 * consultation/politique de rétention (Phase 5).
 *
 * <p>Orchestre {@code audit.domain}. Ne dépend d'aucun autre domaine métier — c'est l'inverse :
 * les autres domaines dépendent de {@code audit}, jamais l'inverse.
 */
package com.apontaja.back.audit.application;
