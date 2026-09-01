package com.apontaja.back.account.web;

import com.apontaja.back.account.application.InvalidRefreshTokenException;
import com.apontaja.back.account.application.RefreshResult;
import com.apontaja.back.account.application.RefreshTokenReuseDetectedException;
import com.apontaja.back.account.application.RefreshTokenService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * Contrairement à register/login, ces deux endpoints s'appuient sur le
 * cookie httpOnly plutôt que sur un Authorization: Bearer — donc permitAll
 * côté authentification JWT (voir SecurityConfig), mais PAS exemptés de
 * CSRF : c'est justement le cas d'usage pour lequel le double-submit token
 * a été mis en place (état modifié via un cookie envoyé automatiquement
 * par le navigateur).
 */
@RestController
@RequestMapping("/api/auth")
class RefreshController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final RefreshTokenService refreshTokenService;

    RefreshController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/refresh")
    ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String rawRefreshToken) {

        if (rawRefreshToken == null) {
            throw new InvalidRefreshTokenException();
        }

        RefreshResult result = refreshTokenService.refresh(rawRefreshToken);

        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, result.rawRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.between(Instant.now(), result.refreshTokenExpiresAt()))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new RefreshResponse(result.accountId(), result.accessToken()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String rawRefreshToken) {

        if (rawRefreshToken != null) {
            refreshTokenService.logout(rawRefreshToken);
        }

        ResponseCookie expiredCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    @ExceptionHandler({InvalidRefreshTokenException.class, RefreshTokenReuseDetectedException.class})
    ResponseEntity<ProblemDetail> handleInvalidRefreshToken(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Session invalide, merci de vous reconnecter.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }
}
