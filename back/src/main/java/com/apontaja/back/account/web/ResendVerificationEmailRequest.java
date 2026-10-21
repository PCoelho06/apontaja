package com.apontaja.back.account.web;

import com.apontaja.back.account.application.FrontendPortal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationEmailRequest(@NotBlank @Email String email, FrontendPortal portal) {
}
