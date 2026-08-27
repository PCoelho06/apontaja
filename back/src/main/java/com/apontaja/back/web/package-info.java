/**
 * Endpoints transverses ne relevant d'aucun domaine métier (ex. {@code /health}).
 *
 * <p>Package volontairement en dehors de la structure par domaine (§2 du fichier de contexte) —
 * choix non explicitement acté dans le fichier de contexte, à confirmer ou ajuster si un besoin
 * de organisation différent apparaît (ex. renommage en {@code shared}/{@code common} si
 * d'autres endpoints transverses s'ajoutent).
 */
package com.apontaja.back.web;
