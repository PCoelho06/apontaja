package com.apontaja.back.account.application;

import java.time.Duration;

/**
 * Texte des emails d'authentification (texte brut). Le portail CLIENT reçoit du
 * portugais (Portugal) ; le portail SALON, et {@code null} (comportement
 * historique), restent en français.
 */
final class AuthEmailComposer {

    record AuthEmail(String subject, String body) {
    }

    private AuthEmailComposer() {
    }

    static AuthEmail verification(FrontendPortal portal, String link, Duration ttl) {
        if (portal == FrontendPortal.CLIENT) {
            return new AuthEmail("Confirme o seu endereço de email",
                    String.join("\n", "Olá,", "",
                            "Para confirmar o seu endereço de email, clique na ligação abaixo (válida durante "
                                    + units(ttl.toHours(), "hora", "horas") + "):",
                            "", link, "", "Se não criou uma conta na Apontaja, pode ignorar esta mensagem.", "",
                            "Com os melhores cumprimentos,", "Apontaja"));
        }
        return new AuthEmail("Confirmez votre adresse email",
                "Cliquez sur ce lien pour confirmer votre email (valable " + ttl.toHours() + "h) : " + link);
    }

    static AuthEmail passwordReset(FrontendPortal portal, String link, Duration ttl) {
        if (portal == FrontendPortal.CLIENT) {
            return new AuthEmail("Reposição da sua palavra-passe", String.join("\n", "Olá,", "",
                    "Para escolher uma nova palavra-passe, clique na ligação abaixo (válida durante "
                            + units(ttl.toMinutes(), "minuto", "minutos") + "):",
                    "", link, "",
                    "Se não fez este pedido, pode ignorar esta mensagem: a sua palavra-passe não será alterada.", "",
                    "Com os melhores cumprimentos,", "Apontaja"));
        }
        return new AuthEmail("Réinitialisation de votre mot de passe",
                "Cliquez sur ce lien pour choisir un nouveau mot de passe (valable " + ttl.toMinutes() + " min) : "
                        + link);
    }

    private static String units(long count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
