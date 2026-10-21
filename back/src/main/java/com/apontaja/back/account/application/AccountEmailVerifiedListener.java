package com.apontaja.back.account.application;

import java.util.UUID;

/**
 * Point d'extension appelé, dans la transaction de la confirmation, quand l'email d'un compte vient d'être vérifié.
 * Implémenté par les domaines qui en dépendent (ex. {@code customer}) : {@code account} reste à la racine du graphe
 * de dépendances.
 */
public interface AccountEmailVerifiedListener {

    void onEmailVerified(UUID accountId, String email);
}
