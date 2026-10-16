package com.apontaja.back.account.web;

import java.util.UUID;

public record MeResponse(UUID accountId, String email, String firstName, String lastName, boolean emailVerified) {
}
