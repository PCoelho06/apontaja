package com.apontaja.back.shared.domain;

/**
 * Hash volontairement rapide (pas Argon2) : le refresh token est un secret à
 * haute entropie généré aléatoirement (256 bits), pas un mot de passe à faible
 * entropie — un hash cryptographique rapide (SHA-256) suffit, un hash lent
 * serait un gaspillage CPU sans bénéfice de sécurité ici.
 */
public interface TokenHasher {
    String hash(String rawValue);
}
