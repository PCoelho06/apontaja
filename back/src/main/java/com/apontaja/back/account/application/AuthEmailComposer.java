package com.apontaja.back.account.application;

import java.time.Duration;

/**
 * Texte des emails d'authentification (texte brut), en portugais du Portugal pour tous les portails :
 * le texte est identique pour le portail CLIENT, le portail SALON et {@code null} (comportement
 * historique). Le paramètre {@code portal} est conservé pour que les appelants n'aient pas à changer ;
 * seule la base des liens dépend du portail (voir {@link FrontendLinks}).
 */
final class AuthEmailComposer {

    record AuthEmail(String subject, String body) {
    }

    private AuthEmailComposer() {
    }

    static AuthEmail verification(FrontendPortal portal, String link, Duration ttl) {
        return new AuthEmail("Confirme o seu endereço de email",
                String.join("\n", "Olá,", "",
                        "Para confirmar o seu endereço de email, clique na ligação abaixo (válida durante "
                                + units(ttl.toHours(), "hora", "horas") + "):",
                        "", link, "", "Se não criou uma conta na Apontaja, pode ignorar esta mensagem.", "",
                        "Com os melhores cumprimentos,", "Apontaja"));
    }

    static AuthEmail passwordReset(FrontendPortal portal, String link, Duration ttl) {
        return new AuthEmail("Reposição da sua palavra-passe", String.join("\n", "Olá,", "",
                "Para escolher uma nova palavra-passe, clique na ligação abaixo (válida durante "
                        + units(ttl.toMinutes(), "minuto", "minutos") + "):",
                "", link, "",
                "Se não fez este pedido, pode ignorar esta mensagem: a sua palavra-passe não será alterada.", "",
                "Com os melhores cumprimentos,", "Apontaja"));
    }

    /** Inscription tentée avec une adresse qui a déjà un compte : liens de connexion et de réinitialisation. */
    static AuthEmail accountExists(FrontendPortal portal, String loginLink, String forgotPasswordLink) {
        return new AuthEmail("Já tem uma conta na Apontaja", String.join("\n", "Olá,", "",
                "Recebemos um pedido para criar uma conta com este endereço de email, mas já existe uma conta "
                        + "associada.",
                "", "Para iniciar sessão, utilize a ligação abaixo:", "", loginLink, "",
                "Se se esqueceu da palavra-passe, pode repô-la aqui:", "", forgotPasswordLink, "",
                "Se não fez este pedido, pode ignorar esta mensagem.", "", "Com os melhores cumprimentos,",
                "Apontaja"));
    }

    private static String units(long count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
