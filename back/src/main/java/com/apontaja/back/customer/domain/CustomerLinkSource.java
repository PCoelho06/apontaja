package com.apontaja.back.customer.domain;

/**
 * Doit rester strictement aligné avec le CHECK de la table
 * {@code salon_customer_link} (V1__initial_schema.sql). {@code SELF} est
 * réservé à l'auto-inscription via le portail client (Phase 4) — non utilisé
 * en Phase 3, où toute création passe par un salon (MANUAL).
 */
public enum CustomerLinkSource {
    MANUAL, SELF
}
