package com.apontaja.back;

import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Vérifie que le contexte Spring démarre correctement, y compris l'application réelle du
 * schéma PostgreSQL via Flyway ({@code V1__initial_schema.sql}) sur un conteneur Testcontainers
 * éphémère (Phase 0, étape 7). C'est la preuve de bon fonctionnement de la migration : si le
 * schéma contient une erreur SQL ou une contrainte invalide, ce test échoue au démarrage.
 */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class BackApplicationTests {

    @Test
    void contextLoadsAndFlywayMigrationApplies() {
        // Le test réussit si le contexte Spring démarre sans exception — ce qui inclut
        // l'exécution complète de V1__initial_schema.sql par Flyway au démarrage.
    }
}
