package com.apontaja.back.account.application;

import java.util.UUID;

public record AccountSummary(UUID accountId, String email, boolean emailVerified) {
}
