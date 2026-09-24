package com.apontaja.back.service.application;

/** Prix et durée effectifs d'un service pour une ressource (surcharge, sinon valeur par défaut). */
public record ServiceTerms(int priceCents, int durationMinutes) {
}
