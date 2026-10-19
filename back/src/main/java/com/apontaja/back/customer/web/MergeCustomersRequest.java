package com.apontaja.back.customer.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

record MergeCustomersRequest(@NotNull UUID survivorId, @NotEmpty @Size(max = 10) List<@NotNull UUID> absorbedIds) {
}
