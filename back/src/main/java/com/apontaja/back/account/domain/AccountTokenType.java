package com.apontaja.back.account.domain;

/**
 * Doit rester strictement aligné avec le CHECK de la table
 * {@code account_token} (V2__account_token.sql). Ne pas renommer une
 * valeur sans migration correspondante.
 */
public enum AccountTokenType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}
