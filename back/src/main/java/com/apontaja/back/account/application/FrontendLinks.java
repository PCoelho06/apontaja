package com.apontaja.back.account.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Construit les liens des emails (vérification, réinitialisation) vers le bon portail. */
@Component
class FrontendLinks {

    // Chemins communs aux deux portails (portugais du Portugal, alignés sur leurs routeurs).
    private static final String CONFIRM_EMAIL = "/confirmar-email?token=";
    private static final String RESET_PASSWORD = "/repor-palavra-passe?token=";

    private final String salonBaseUrl;
    private final String clientBaseUrl;

    FrontendLinks(@Value("${apontaja.frontend.base-url:http://localhost:5173}") String salonBaseUrl,
            @Value("${apontaja.frontend.client-base-url:http://localhost:5174}") String clientBaseUrl) {
        this.salonBaseUrl = salonBaseUrl;
        this.clientBaseUrl = clientBaseUrl;
    }

    /**
     * @param portal portail visé ; {@code null} = portail salon (comportement historique)
     * @param path   chemin et paramètres, commençant par « / »
     */
    String link(FrontendPortal portal, String path) {
        String base = portal == FrontendPortal.CLIENT ? clientBaseUrl : salonBaseUrl;
        return base + path;
    }

    /** Lien de confirmation d'email vers la page du portail visé ({@code null} = portail salon). */
    String confirmEmailLink(FrontendPortal portal, String rawToken) {
        return link(portal, CONFIRM_EMAIL + rawToken);
    }

    /** Lien de réinitialisation du mot de passe vers la page du portail visé ({@code null} = portail salon). */
    String resetPasswordLink(FrontendPortal portal, String rawToken) {
        return link(portal, RESET_PASSWORD + rawToken);
    }
}
