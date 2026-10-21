package com.apontaja.back.account.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Construit les liens des emails (vérification, réinitialisation) vers le bon portail. */
@Component
class FrontendLinks {

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
}
