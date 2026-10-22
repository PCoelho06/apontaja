package com.apontaja.back.customer.application;

/** Coordonnées d'un compte client pour lui écrire : adresse email et prénom (jamais le nom complet). */
public record ClientContact(String email, String firstName) {
}
