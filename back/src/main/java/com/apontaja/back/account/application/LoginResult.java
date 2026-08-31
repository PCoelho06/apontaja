package com.apontaja.back.account.application;

import java.time.Instant;
import java.util.UUID;

public record LoginResult(
        UUID accountId,
        String email,
        String accessToken,
        String rawRefreshToken,
        Instant refreshTokenExpiresAt) {
}
