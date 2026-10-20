package com.apontaja.back.customer.domain;

import java.util.UUID;

/** Sérialise les fusions concurrentes d'un même salon (verrou levé à la fin de la transaction). */
public interface CustomerMergeLock {

    void lockSalon(UUID salonId);
}
