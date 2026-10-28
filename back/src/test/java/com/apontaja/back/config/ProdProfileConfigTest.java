package com.apontaja.back.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fige le contenu de application-prod.yml : chaque valeur sensible ou propre à l'environnement est un
 * placeholder SANS valeur par défaut (un défaut ferait démarrer la production avec une valeur de dev).
 */
class ProdProfileConfigTest {

    private static final List<String> MANDATORY_ENVIRONMENT_KEYS = List.of(
            "server.tomcat.remoteip.internal-proxies",
            "apontaja.security.jwt.secret",
            "apontaja.frontend.base-url",
            "apontaja.frontend.client-base-url");

    private static Properties prod() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }

    @Test
    void les_valeurs_d_environnement_sont_des_placeholders_sans_defaut() {
        Properties prod = prod();

        for (String key : MANDATORY_ENVIRONMENT_KEYS) {
            assertThat(prod.getProperty(key)).as(key).matches("\\$\\{[A-Z_]+}");
        }
    }

    @Test
    void le_secret_jwt_et_les_proxys_viennent_des_bonnes_variables() {
        Properties prod = prod();

        assertThat(prod.getProperty("apontaja.security.jwt.secret")).isEqualTo("${APONTAJA_JWT_SECRET}");
        assertThat(prod.getProperty("server.tomcat.remoteip.internal-proxies"))
                .isEqualTo("${APONTAJA_TRUSTED_PROXIES}");
    }

    @Test
    void les_en_tetes_du_proxy_sont_appliques_par_tomcat() {
        Properties prod = prod();

        assertThat(prod.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(prod.getProperty("server.tomcat.remoteip.remote-ip-header")).isEqualTo("X-Forwarded-For");
        assertThat(prod.getProperty("server.tomcat.remoteip.protocol-header")).isEqualTo("X-Forwarded-Proto");
    }

    @Test
    void la_limitation_de_debit_est_active_en_production() {
        assertThat(prod().getProperty("apontaja.security.rate-limiting.enabled")).isEqualTo("true");
    }
}
