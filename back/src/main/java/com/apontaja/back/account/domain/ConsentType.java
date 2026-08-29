package com.apontaja.back.account.domain;

/**
 * Doit rester strictement aligné avec le CHECK de la table
 * {@code consent_record} dans apontaja-schema.sql. Ne pas renommer une
 * valeur sans migration Flyway correspondante.
 */
public enum ConsentType {
    TOS,
    PRIVACY,
    MARKETING_PLATFORM
}
