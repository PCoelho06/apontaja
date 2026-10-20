package com.apontaja.back.service.application;

import java.util.UUID;

/** Prix et durée effectifs d'une prestation pour une ressource donnée (surcharge sinon défaut). */
public record ResourceTerms(UUID resourceId, int priceCents, int durationMinutes) {
}
