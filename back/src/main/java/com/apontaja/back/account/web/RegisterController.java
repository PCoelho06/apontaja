package com.apontaja.back.account.web;

import com.apontaja.back.account.application.RegisterAccountCommand;
import com.apontaja.back.account.application.RegisterAccountService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class RegisterController {

    private static final Logger LOG = LoggerFactory.getLogger(RegisterController.class);

    private final RegisterAccountService registerAccountService;

    RegisterController(RegisterAccountService registerAccountService) {
        this.registerAccountService = registerAccountService;
    }

    /** 202 sans corps dans tous les cas valides : la réponse ne révèle pas si l'adresse avait déjà un compte. */
    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        registerAccountService.register(new RegisterAccountCommand(request.email(), request.firstName(),
                request.lastName(), request.password(), request.portal()));

        return ResponseEntity.accepted().build();
    }

    /**
     * Deux inscriptions simultanées de la même adresse : l'index unique fait échouer la seconde. Même réponse
     * que pour une adresse connue (l'adresse n'est jamais journalisée).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Void> handleConcurrentRegistration(DataIntegrityViolationException ex) {
        LOG.warn("Inscription concurrente ou contrainte violée ({}), réponse neutre.", ex.getClass().getSimpleName());
        return ResponseEntity.accepted().build();
    }
}
