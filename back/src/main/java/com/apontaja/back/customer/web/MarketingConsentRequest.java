package com.apontaja.back.customer.web;

import jakarta.validation.constraints.NotNull;

public record MarketingConsentRequest(@NotNull Boolean consented) {
}
