/**
 * Domaine transverse purement technique — à la différence de {@code audit},
 * aucun sens métier. Contient les ports/adapters partagés par tous les domaines
 * (génération d'ID, etc.).
 *
 * <p>
 * Analogue à {@code audit} dans le graphe de dépendances (§2) : tous les
 * domaines métier peuvent en dépendre ; {@code shared} ne dépend lui-même
 * d'aucun domaine métier.
 */
package com.apontaja.back.shared;
