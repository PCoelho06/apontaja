package com.apontaja.back.config;

import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Profil « prod » sur un vrai Tomcat : le client simulé (127.0.0.1) est le proxy de confiance, il transmet
 * l'IP du « vrai » client dans X-Forwarded-For. Prouve que la limitation de débit voit l'IP du client et
 * que HSTS suit X-Forwarded-Proto. Si les noms de propriétés Tomcat de application-prod.yml étaient faux,
 * ce test échouerait (tous les « clients » partageraient le seau de 127.0.0.1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "APONTAJA_TRUSTED_PROXIES=127\\.0\\.0\\.1|::1|0:0:0:0:0:0:0:1",
        "APONTAJA_JWT_SECRET=a-32-byte-minimum-test-secret-key-for-hs256!!",
        "APONTAJA_SALON_URL=https://salon.example.test",
        "APONTAJA_CLIENT_URL=https://app.example.test" })
@ActiveProfiles("prod")
@Import(PostgresTestcontainersConfiguration.class)
class ProxyForwardingIT {

    private static final String CLIENT_A = "198.51.100.7";
    private static final String CLIENT_B = "198.51.100.8";

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> get(String path, String forwardedFor, String forwardedProto) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        if (forwardedProto != null) {
            request.header("X-Forwarded-Proto", forwardedProto);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String slotsPath() {
        return "/api/public/salons/" + UUID.randomUUID() + "/services/" + UUID.randomUUID() + "/slots";
    }

    @Test
    void la_limitation_de_debit_utilise_l_ip_transmise_par_le_proxy_de_confiance() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(get(slotsPath(), CLIENT_A, "https").statusCode()).isEqualTo(404);
        }

        assertThat(get(slotsPath(), CLIENT_A, "https").statusCode()).isEqualTo(429);
        // Un autre client derrière le même proxy garde son propre seau.
        assertThat(get(slotsPath(), CLIENT_B, "https").statusCode()).isEqualTo(404);
    }

    @Test
    void hsts_suit_x_forwarded_proto() throws Exception {
        assertThat(get("/health", CLIENT_B, "https").headers().firstValue("Strict-Transport-Security"))
                .hasValueSatisfying(value -> assertThat(value).contains("max-age=31536000"));
        assertThat(get("/health", CLIENT_B, "http").headers().firstValue("Strict-Transport-Security")).isEmpty();
    }
}
