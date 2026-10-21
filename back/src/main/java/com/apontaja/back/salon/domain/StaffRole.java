package com.apontaja.back.salon.domain;

/**
 * Doit rester strictement aligné avec le CHECK de la table
 * {@code staff_membership} de V1__initial_schema.sql. Ne pas renommer une
 * valeur sans migration correspondante.
 */
public enum StaffRole {
    OWNER, MANAGER, EMPLOYEE
}
