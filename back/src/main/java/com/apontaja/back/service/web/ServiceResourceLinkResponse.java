package com.apontaja.back.service.web;

import java.util.UUID;

public record ServiceResourceLinkResponse(UUID resourceId, Integer overridePriceCents,
        Integer overrideDurationMinutes, int effectivePriceCents, int effectiveDurationMinutes) {
}
