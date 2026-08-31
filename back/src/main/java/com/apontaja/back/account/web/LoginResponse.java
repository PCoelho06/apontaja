package com.apontaja.back.account.web;

import java.util.UUID;

public record LoginResponse(UUID accountId, String email, String accessToken) {
}
