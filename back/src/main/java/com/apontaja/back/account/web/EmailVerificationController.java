package com.apontaja.back.account.web;

import com.apontaja.back.account.application.EmailVerificationService;
import com.apontaja.back.account.application.InvalidOrExpiredTokenException;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/confirm-email")
    ResponseEntity<Void> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        emailVerificationService.confirm(request.token());
        return ResponseEntity.noContent().build();
    }

    /** Toujours 204, que l'email existe, soit déjà vérifié, ou non — anti-énumération. */
    @PostMapping("/resend-verification-email")
    ResponseEntity<Void> resendVerificationEmail(@Valid @RequestBody ResendVerificationEmailRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidOrExpiredTokenException.class)
    ResponseEntity<ProblemDetail> handleInvalidToken(InvalidOrExpiredTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        return ResponseEntity.badRequest().body(problem);
    }
}
