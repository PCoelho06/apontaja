package com.apontaja.back.organization.domain;

/**
 * Doit rester strictement aligné avec le CHECK de la table
 * {@code organization_membership} de V1__initial_schema.sql. Ne pas renommer
 * une valeur sans migration correspondante.
 */
public enum OrganizationRole {
    OWNER
}
