package com.apontaja.back.account.web;

import java.util.UUID;

public record RegisterResponse(UUID accountId, String email) {
}
