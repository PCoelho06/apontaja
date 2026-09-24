package com.apontaja.back.service.application;

import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.ServiceResource;

import java.util.UUID;

public record ServiceResourceLinkSummary(UUID resourceId, Integer overridePriceCents,
        Integer overrideDurationMinutes, int effectivePriceCents, int effectiveDurationMinutes) {

    static ServiceResourceLinkSummary of(ServiceResource link, SalonService service) {
        return new ServiceResourceLinkSummary(link.getResourceId(), link.getOverridePriceCents(),
                link.getOverrideDurationMinutes(), effectivePrice(link, service), effectiveDuration(link, service));
    }

    static int effectivePrice(ServiceResource link, SalonService service) {
        return link.getOverridePriceCents() != null ? link.getOverridePriceCents() : service.getDefaultPriceCents();
    }

    static int effectiveDuration(ServiceResource link, SalonService service) {
        return link.getOverrideDurationMinutes() != null ? link.getOverrideDurationMinutes()
                : service.getDefaultDurationMinutes();
    }
}
