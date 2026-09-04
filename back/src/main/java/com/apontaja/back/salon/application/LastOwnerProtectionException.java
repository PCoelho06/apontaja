package com.apontaja.back.salon.application;

public class LastOwnerProtectionException extends RuntimeException {

    public LastOwnerProtectionException() {
        super("Impossible de retirer ou rétrograder le dernier propriétaire de ce salon.");
    }
}
