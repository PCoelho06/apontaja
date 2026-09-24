package com.apontaja.back.resource.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Semaine complète (remplacement). Liste vide = effacer. */
public record ScheduleRequest(@NotNull @Size(max = 100) List<@Valid ScheduleSlotRequest> slots) {
}
