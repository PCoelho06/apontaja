package com.apontaja.back.resource.domain;

/**
 * Doit rester strictement aligné avec le CHECK de la table {@code resource}
 * (V1__initial_schema.sql). Ne pas renommer une valeur sans migration.
 */
public enum ResourceType {
    EMPLOYEE, MACHINE
}
