package com.apontaja.back.account.domain;

/** Génère la valeur en clair du refresh token (256 bits, aléatoire). */
public interface OpaqueTokenGenerator {
    String generate();
}
