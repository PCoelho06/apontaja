package com.apontaja.back.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint de santé minimal pour la Phase 0 / étape 2.
 *
 * <p>Placé dans un package {@code web} générique et non dans un package de domaine métier : la
 * structure par domaine (§2 du fichier de contexte : account, organization, salon, ...) sera mise
 * en place à l'étape 3 de la Phase 0. Ce fichier sera déplacé/réorganisé à ce moment-là si
 * pertinent — /health n'appartient à aucun domaine métier.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
