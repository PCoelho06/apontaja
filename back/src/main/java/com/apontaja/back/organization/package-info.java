/**
 * Domaine {@code organization} — regroupement "société" au-dessus des salons
 * ({@code Organization}, {@code OrganizationMembership}).
 *
 * <p><b>Position dans le graphe de dépendances</b> (§2) : dépend de {@code account}. Donne une
 * capacité administrative sur ses salons (OWNER), mais toute logique métier reste systématiquement
 * scopée au salon — ce domaine ne doit jamais servir à contourner le scoping par {@code salonId}.
 */
package com.apontaja.back.organization;
