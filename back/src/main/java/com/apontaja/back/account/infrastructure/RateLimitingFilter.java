package com.apontaja.back.account.infrastructure;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Limitation par IP (request.getRemoteAddr(), volontairement PAS
 * X-Forwarded-For — aucun reverse proxy de confiance identifié à ce stade,
 * cf. §6 [OPEN] du contexte ; un attaquant pourrait sinon usurper l'en-tête
 * pour contourner la limite. À revoir en Phase 5 si un proxy est introduit
 * devant l'appli, avec une liste d'IP de proxys de confiance explicite.
 *
 * <p>Bucket4j en mode purement local (pas de ProxyManager/stockage
 * externe type Redis) : suffisant pour une seule instance. Cache Caffeine
 * plutôt qu'une simple Map pour éviter une fuite mémoire si l'appli tourne
 * longtemps avec beaucoup d'IP distinctes (éviction automatique).
 *
 * <p>Désactivable via {@code apontaja.security.rate-limiting.enabled} —
 * utilisé par les tests d'intégration fonctionnels (register/login/refresh)
 * qui n'ont pas à être couplés à l'état d'un bean singleton partagé dans
 * tout le contexte Spring. Seul RateLimitingIT le garde actif.
 *
 * <p>Pour ajouter un nouvel endpoint à limiter (ex. confirm-password en
 * tranche 7) : une ligne dans LIMITED_PATHS, rien d'autre.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Map<String, Supplier<Bandwidth>> LIMITED_PATHS = Map.of(
            "/api/auth/register", () -> Bandwidth.builder().capacity(5)
                    .refillGreedy(5, Duration.ofHours(1)).build(),
            "/api/auth/login", () -> Bandwidth.builder().capacity(10)
                    .refillGreedy(10, Duration.ofMinutes(15)).build(),
            "/api/auth/refresh", () -> Bandwidth.builder().capacity(30)
                    .refillGreedy(30, Duration.ofMinutes(15)).build());

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(100_000)
            .build();

    private final boolean enabled;

    public RateLimitingFilter(@Value("${apontaja.security.rate-limiting.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        Supplier<Bandwidth> limitSupplier = LIMITED_PATHS.get(path);

        if (limitSupplier == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = path + ':' + request.getRemoteAddr();
        Bucket bucket = buckets.get(key, k -> Bucket.builder().addLimit(limitSupplier.get()).build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":429,\"detail\":\"Trop de tentatives, réessayez plus tard.\"}");
    }
}
