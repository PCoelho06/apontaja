package com.apontaja.back.account.application;

public record RegisterAccountCommand(String email, String firstName, String lastName, String rawPassword) {
}
