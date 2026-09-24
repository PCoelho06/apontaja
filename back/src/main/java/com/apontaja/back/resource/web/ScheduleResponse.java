package com.apontaja.back.resource.web;

import java.util.List;

public record ScheduleResponse(List<ScheduleSlotResponse> slots) {
}
