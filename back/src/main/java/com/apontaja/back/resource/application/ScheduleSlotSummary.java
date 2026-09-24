package com.apontaja.back.resource.application;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleSlotSummary(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
