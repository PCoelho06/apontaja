package com.apontaja.back.resource.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClosureRequest(@NotBlank String startAt, @NotBlank String endAt, @Size(max = 500) String reason) {
}
