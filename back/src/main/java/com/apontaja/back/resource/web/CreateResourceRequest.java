package com.apontaja.back.resource.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateResourceRequest(@NotBlank @Size(max = 100) String name, @NotBlank String type) {
}
