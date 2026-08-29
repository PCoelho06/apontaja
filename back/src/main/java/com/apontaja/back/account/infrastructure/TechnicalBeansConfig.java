package com.apontaja.back.account.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/**
 * Beans techniques transverses posés ici faute d'un meilleur endroit pour
 * l'instant (premier domaine à en avoir besoin). À extraire vers un package
 * partagé si un deuxième domaine en a besoin — pas anticipé maintenant
 * (YAGNI), un bean Spring reste utilisable dans toute l'appli quel que soit
 * le package qui le déclare.
 */
@Configuration
class TechnicalBeansConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        // Argon2id, paramètres OWASP Password Storage Cheat Sheet (première
        // option recommandée) : m=19456 KiB (19 MiB), t=2 itérations, p=1.
        // À réévaluer avec un vrai benchmark serveur (cible ~250-500ms/hash) —
        // pas fait ici, aucune infra de prod encore choisie (§6 du contexte).
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
