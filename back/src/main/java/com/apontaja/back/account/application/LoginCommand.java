package com.apontaja.back.account.application;

public record LoginCommand(String email, String rawPassword, String deviceInfo) {
}
