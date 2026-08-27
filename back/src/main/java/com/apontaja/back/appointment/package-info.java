/**
 * Domaine {@code appointment} — le cœur du produit : rendez-vous ({@code Appointment},
 * {@code AppointmentResource}).
 *
 * <p><b>Position dans le graphe de dépendances</b> (§2) : sommet du graphe, dépend de
 * {@code salon}, {@code resource}, {@code service}, {@code customer}. Rien n'en dépend.
 *
 * <p><b>Règle de scoping explicite</b> : {@code AppointmentResource.resourceId} doit
 * obligatoirement appartenir au même salon que {@code Appointment.salonId}. Garanti côté service
 * applicatif (validation systématique avant écriture) et renforcé côté base de données par une
 * contrainte d'exclusion PostgreSQL ({@code EXCLUDE} + {@code btree_gist}, voir
 * {@code apontaja-schema.sql}) qui empêche mécaniquement tout chevauchement de réservation sur
 * une même ressource.
 */
package com.apontaja.back.appointment;
