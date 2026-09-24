package com.apontaja.back.service.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Surcharges optionnelles : null = utiliser la valeur par défaut du service. */
public record ServiceResourceLinkRequest(@Min(0) Integer overridePriceCents,
        @Min(1) @Max(1440) Integer overrideDurationMinutes) {
}
