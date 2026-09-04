package com.apontaja.back.salon.web;

import jakarta.validation.constraints.NotBlank;

public record CreateSalonRequest(@NotBlank String name, @NotBlank String address, @NotBlank String postalCode,
        @NotBlank String city, @NotBlank String country, @NotBlank String timezone, String phone) {
}
