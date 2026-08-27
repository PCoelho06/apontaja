package com.apontaja.back;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Vérifie que le contexte Spring démarre correctement sans base de données configurée
 * (cf. exclusions temporaires dans {@link BackApplication}, à retirer à l'étape 7).
 */
@SpringBootTest
class BackApplicationTests {

    @Test
    void contextLoads() {
        // Le test réussit si le contexte Spring démarre sans exception.
    }
}
