package com.apontaja.back.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Fournit un vrai PostgreSQL éphémère (Testcontainers) à tout test {@code @SpringBootTest} qui
 * l'importe, conformément à la stratégie de tests actée (§2 du fichier de contexte : JUnit5 +
 * Testcontainers, PostgreSQL réel, pas de simulation).
 *
 * <p>Usage : {@code @Import(PostgresTestcontainersConfiguration.class)} sur la classe de test.
 * {@code @ServiceConnection} enregistre automatiquement les propriétés de connexion
 * (url/username/password) auprès de Spring — aucune configuration manuelle nécessaire, y
 * compris pour Flyway qui appliquera {@code V1__initial_schema.sql} sur ce conteneur au
 * démarrage du contexte.
 *
 * <p><b>Version PostgreSQL épinglée à 16</b> (pas {@code latest}) : Flyway tel que géré par
 * Spring Boot 4.0.x ne supporte pas encore PostgreSQL 18 au moment de l'écriture (bug connu,
 * "Unsupported Database"). 16 est une version stable largement testée avec l'écosystème
 * Flyway/Testcontainers actuel.
 *
 * <p><b>Prérequis local</b> : Docker doit tourner sur la machine pour que ces tests passent
 * (que ce soit en local via {@code mvn clean verify} ou en CI — les runners GitHub-hosted ont
 * Docker préinstallé, aucune configuration supplémentaire nécessaire côté
 * {@code .github/workflows/ci.yml}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
