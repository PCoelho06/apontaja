package com.apontaja.back.appointment.domain;

/**
 * Doit rester strictement aligné avec le CHECK de la table {@code appointment}
 * (V1__initial_schema.sql). Ne pas renommer une valeur sans migration
 * correspondante. Seul {@code SCHEDULED} est produit en Phase 3 tranche 6
 * (création) — les transitions (CONFIRMED, COMPLETED, CANCELLED, NO_SHOW)
 * arrivent avec les tranches 7-8.
 */
public enum AppointmentStatus {
    SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW
}
