package com.apontaja.back.account.web;

import java.util.UUID;

public record MeResponse(UUID accountId, String email, boolean emailVerified) {
}
