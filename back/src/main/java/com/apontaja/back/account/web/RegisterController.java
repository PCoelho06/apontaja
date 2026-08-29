package com.apontaja.back.account.web;

import com.apontaja.back.account.application.EmailAlreadyUsedException;
import com.apontaja.back.account.application.RegisterAccountCommand;
import com.apontaja.back.account.application.RegisterAccountResult;
import com.apontaja.back.account.application.RegisterAccountService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// ADAPTER LE PREFIXE si votre convention de routage diffère (je pars sur
// /api/auth, à confirmer/ajuster — voir ma question sur SecurityConfig).
@RestController
@RequestMapping("/api/auth")
class RegisterController {

    private final RegisterAccountService registerAccountService;

    RegisterController(RegisterAccountService registerAccountService) {
        this.registerAccountService = registerAccountService;
    }

    @PostMapping("/register")
    ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterAccountResult result = registerAccountService.register(
                new RegisterAccountCommand(request.email(), request.password()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(result.accountId(), result.email()));
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    ResponseEntity<ProblemDetail> handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
