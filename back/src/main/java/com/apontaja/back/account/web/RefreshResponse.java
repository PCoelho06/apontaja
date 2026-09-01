package com.apontaja.back.account.web;

import java.util.UUID;

public record RefreshResponse(UUID accountId, String accessToken) {
}
