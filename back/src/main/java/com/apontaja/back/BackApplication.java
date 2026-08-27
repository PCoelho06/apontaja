package com.apontaja.back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;

/**
 * Point d'entrée de l'application Apontaja.
 *
 * <p>Phase 0 / étape 2 — squelette minimal (Web, Security, Data JPA, Validation) avec un simple
 * endpoint /health. Aucune base de données n'est encore configurée à ce stade : le schéma
 * PostgreSQL (apontaja-schema.sql) ne sera appliqué via Flyway qu'à l'étape 7 de la Phase 0.
 *
 * <p><b>TODO étape 7</b> : une fois la DataSource PostgreSQL + Flyway configurés, retirer les
 * exclusions ci-dessous ({@link DataSourceAutoConfiguration}, {@link HibernateJpaAutoConfiguration},
 * {@link DataSourceTransactionManagerAutoConfiguration}) pour réactiver l'auto-configuration JPA
 * normale. Sans base de données, les laisser actives est nécessaire : sinon le contexte Spring
 * échoue au démarrage faute de DataSource disponible.
 */
@SpringBootApplication(
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class
        }
)
public class BackApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackApplication.class, args);
    }
}
