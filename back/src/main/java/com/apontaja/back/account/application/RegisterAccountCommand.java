package com.apontaja.back.account.application;

public record RegisterAccountCommand(String email, String rawPassword) {
}
