package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.AvailabilityEngine;
import com.apontaja.back.resource.domain.AvailabilityInput;
import com.apontaja.back.resource.domain.TimeInterval;
import com.apontaja.back.resource.domain.WeeklyWindow;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/** Implémentation de {@link AvailabilityPlan} déléguant au moteur pur {@link AvailabilityEngine}. */
final class EngineAvailabilityPlan implements AvailabilityPlan {

    private final ZoneId zone;
    private final List<WeeklyWindow> salonHours;
    private final List<WeeklyWindow> resourceHours;
    private final List<TimeInterval> salonClosures;
    private final List<TimeInterval> resourceClosures;

    EngineAvailabilityPlan(ZoneId zone, List<WeeklyWindow> salonHours, List<WeeklyWindow> resourceHours,
            List<TimeInterval> salonClosures, List<TimeInterval> resourceClosures) {
        this.zone = zone;
        this.salonHours = List.copyOf(salonHours);
        this.resourceHours = List.copyOf(resourceHours);
        this.salonClosures = List.copyOf(salonClosures);
        this.resourceClosures = List.copyOf(resourceClosures);
    }

    @Override
    public boolean isAvailable(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            return false;
        }
        AvailabilityInput input = new AvailabilityInput(new TimeInterval(start, end), zone, salonHours,
                resourceHours, salonClosures, resourceClosures);
        return AvailabilityEngine.check(input).isAvailable();
    }
}
