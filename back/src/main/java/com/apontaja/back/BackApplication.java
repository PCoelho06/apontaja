package com.apontaja.back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée de l'application Apontaja.
 *
 * <p>Depuis la Phase 0 / étape 7, l'auto-configuration JPA/DataSource/Flyway est active sans
 * exclusion (contrairement aux étapes 2-6, où elle était temporairement désactivée faute de base
 * de données configurée — voir l'historique dans {@code back/README.md}). Le schéma PostgreSQL
 * de référence ({@code apontaja-schema.sql}, à la racine de {@code back/}) est appliqué via
 * Flyway au démarrage, depuis sa copie dans {@code src/main/resources/db/migration/}.
 *
 * <p>Nécessite une base de données PostgreSQL accessible au démarrage — voir le profil Spring
 * {@code local} ({@code application-local.yml}, non commité) pour le dev local. Aucun
 * hébergement de production n'est encore décidé (voir §6 du fichier de contexte).
 */
@SpringBootApplication
public class BackApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackApplication.class, args);
    }
}
