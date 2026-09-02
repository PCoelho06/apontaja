package com.apontaja.back.account.web;

import com.apontaja.back.account.application.AccountQueryService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Premier endpoint réellement protégé de l'application (pas dans la liste
 * permitAll de SecurityConfig, donc couvert par anyRequest().authenticated()
 * sans configuration supplémentaire). GET, donc pas concerné par CSRF (ne
 * s'applique qu'aux méthodes non sûres).
 */
@RestController
@RequestMapping("/api/account")
class AccountController {

    private final AccountQueryService accountQueryService;

    AccountController(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    /**
     * 401 (pas 403) si le compte n'existe plus ou a été soft-deleted :
     * contrairement à un token absent/invalide (rejeté en amont par le
     * filtre de sécurité, avant d'atteindre ce contrôleur), ici le JWT est
     * cryptographiquement valide — c'est l'état du compte qui invalide la
     * requête. Distinction volontaire, documentée dans AccountControllerIT.
     *
     * <p>Note de conception : le filtre JWT ne vérifie jamais l'état du
     * compte en base à chaque requête (coût d'une requête DB par appel,
     * défait une partie de l'intérêt d'un JWT stateless) — la fenêtre de
     * validité résiduelle d'un token émis avant une suppression de compte
     * est donc au maximum la durée de vie de l'access token (15 min par
     * défaut). Acceptable en v1 : aucune fonctionnalité de suppression de
     * compte n'existe encore, ce cas n'est pas atteignable en pratique
     * aujourd'hui — mais gardé en tête pour quand ce sera le cas.
     */
    @GetMapping("/me")
    ResponseEntity<MeResponse> me(@AuthenticationPrincipal UUID accountId) {
        return accountQueryService.findAliveById(accountId)
                .map(summary -> ResponseEntity.ok(
                        new MeResponse(summary.accountId(), summary.email(), summary.emailVerified())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
