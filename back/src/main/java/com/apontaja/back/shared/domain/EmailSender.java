package com.apontaja.back.shared.domain;

/**
 * Implémenté en v1 par un adapter qui logue le lien au lieu d'envoyer un vrai
 * email (décision actée en session : pas de provider configuré tant que
 * l'hébergement de production n'est pas choisi, cf. §6 [OPEN]).
 */
public interface EmailSender {
    void send(String to, String subject, String body);
}
