package com.apontaja.back.account.application;

import java.util.UUID;
import java.util.Locale;

public record AccountSummary(UUID accountId, String email, boolean emailVerified, String firstName, String lastName) {

    /**
     * « Pierre D. » : prénom suivi de l'initiale du nom (les noms sont enregistrés
     * sans espaces autour).
     */
    public String displayName() {
        if (lastName.isEmpty()) {
            return firstName;
        }
        String initial = lastName.substring(0, lastName.offsetByCodePoints(0, 1));
        return firstName + " " + initial.toUpperCase(Locale.ROOT) + ".";
    }
}
