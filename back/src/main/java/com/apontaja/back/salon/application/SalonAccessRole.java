package com.apontaja.back.salon.application;

/**
 * Rôle effectif d'un compte sur un salon dans le contexte d'une liste —
 * distinct de {@code StaffRole} (domaine) : ajoute la valeur
 * {@code ORGANIZATION_OWNER} pour le cas d'accès via OrganizationMembership
 * OWNER sans StaffMembership explicite (voir SalonAccessGuard).
 */
public enum SalonAccessRole {
    OWNER, MANAGER, EMPLOYEE, ORGANIZATION_OWNER
}
