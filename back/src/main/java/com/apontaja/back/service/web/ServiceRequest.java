package com.apontaja.back.service.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps commun à la création et à la mise à jour (PUT = remplacement complet). */
public record ServiceRequest(@NotBlank @Size(max = 100) String name, @Size(max = 1000) String description,
        @NotNull @Min(1) @Max(1440) Integer defaultDurationMinutes, @NotNull @Min(0) Integer defaultPriceCents) {
}
