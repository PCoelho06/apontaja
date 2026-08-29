package com.apontaja.back.account.application;

import java.util.UUID;

public record RegisterAccountResult(UUID accountId, String email) {
}
