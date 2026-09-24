package com.apontaja.back.resource.web;

import jakarta.validation.constraints.NotBlank;

public record ScheduleSlotRequest(@NotBlank String dayOfWeek, @NotBlank String startTime,
        @NotBlank String endTime) {
}
