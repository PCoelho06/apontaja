package com.apontaja.back.salon.application;

import com.apontaja.back.salon.domain.StaffRole;

import java.time.Duration;

/** Texte de l'email d'invitation d'un membre du staff (texte brut, portugais du Portugal). */
final class StaffInvitationEmailComposer {

    record InvitationEmail(String subject, String body) {
    }

    private StaffInvitationEmailComposer() {
    }

    static InvitationEmail compose(StaffRole role, String link, Duration ttl) {
        return new InvitationEmail("Convite para se juntar a um salão na Apontaja",
                String.join("\n", "Olá,", "",
                        "Recebeu um convite para se juntar a um salão na Apontaja (função: " + roleLabel(role) + ").",
                        "", "Para aceitar o convite, clique na ligação abaixo (válida durante " + days(ttl) + "):",
                        "", link, "", "Se não estava à espera deste convite, pode ignorar esta mensagem.", "",
                        "Com os melhores cumprimentos,", "Apontaja"));
    }

    private static String roleLabel(StaffRole role) {
        return switch (role) {
            case OWNER -> "proprietário";
            case MANAGER -> "gestor";
            case EMPLOYEE -> "colaborador";
        };
    }

    private static String days(Duration ttl) {
        long count = ttl.toDays();
        return count + (count == 1 ? " dia" : " dias");
    }
}
