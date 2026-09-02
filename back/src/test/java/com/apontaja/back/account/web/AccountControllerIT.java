package com.apontaja.back.account.web;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice de tests d'autorisation — le pattern à répliquer en Phase 2 pour les
 * futurs endpoints scopés par salon/rôle (OWNER/MANAGER/EMPLOYEE via
 * StaffMembership, qui n'existe pas encore). Rien à tester côté RBAC ici :
 * /api/account/me est le seul endpoint protégé existant, donc la matrice ne
 * couvre que la validité du token (absent/malformé/expiré/altéré) et l'état du
 * compte (vivant/supprimé/inexistant).
 *
 * <p>
 * Les tokens sont construits directement ici avec le même secret que
 * l'application (via @Value, pas via le bean interne JwtAccessTokenIssuer,
 * package-privé) — donne un contrôle total sur les cas invalides (expiré,
 * mauvaise signature) sans dépendre de l'horloge réelle.
 *
 * <p>
 * ATTENTION : les statuts 403 attendus ci-dessous (rejet AVANT même d'atteindre
 * le contrôleur, par le filtre de sécurité) reposent sur le comportement par
 * défaut de Spring Security quand httpBasic et formLogin sont désactivés
 * (Http403ForbiddenEntryPoint implicite). Si votre environnement renvoie 401 à
 * ce niveau plutôt que 403, dites-le-moi — j'ajusterai les assertions plutôt
 * que de deviner à l'aveugle une seconde fois.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class AccountControllerIT {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Value("${apontaja.security.jwt.secret}")
    private String jwtSecret;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private String buildToken(UUID accountId, Instant issuedAt, Instant expiresAt, SecretKey key) {
        return Jwts.builder().subject(accountId.toString()).issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt)).signWith(key).compact();
    }

    private Account createAliveAccount() {
        Account account = new Account(UUID.randomUUID(), "me-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("un-mot-de-passe-suffisant"), clock.instant());
        return accountRepository.save(account);
    }

    @Test
    void sans_token_repond_403() throws Exception {
        mockMvc.perform(get("/api/account/me")).andExpect(status().isForbidden());
    }

    @Test
    void avec_un_header_authorization_malforme_repond_403() throws Exception {
        mockMvc.perform(get("/api/account/me").header(HttpHeaders.AUTHORIZATION, "PasDuToutUnBearer xyz"))
                .andExpect(status().isForbidden());
    }

    @Test
    void avec_un_token_expire_repond_403() throws Exception {
        Account account = createAliveAccount();
        Instant now = clock.instant();
        String expiredToken = buildToken(account.getId(), now.minus(1, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.MINUTES), signingKey());

        mockMvc.perform(get("/api/account/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void avec_un_token_signe_par_un_autre_secret_repond_403() throws Exception {
        Account account = createAliveAccount();
        Instant now = clock.instant();
        SecretKey otherKey = Keys
                .hmacShaKeyFor("un-tout-autre-secret-de-32-octets-minimum!!".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = buildToken(account.getId(), now, now.plus(ACCESS_TOKEN_TTL), otherKey);

        mockMvc.perform(get("/api/account/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void avec_un_token_valide_pour_un_compte_vivant_repond_200() throws Exception {
        Account account = createAliveAccount();
        Instant now = clock.instant();
        String token = buildToken(account.getId(), now, now.plus(ACCESS_TOKEN_TTL), signingKey());

        mockMvc.perform(get("/api/account/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(account.getId().toString()))
                .andExpect(jsonPath("$.email").value(account.getEmail()))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void avec_un_token_valide_pour_un_compte_supprime_repond_401() throws Exception {
        Account account = createAliveAccount();
        Instant now = clock.instant();
        account.softDelete(now);
        accountRepository.save(account);

        String token = buildToken(account.getId(), now, now.plus(ACCESS_TOKEN_TTL), signingKey());

        mockMvc.perform(get("/api/account/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void avec_un_token_pour_un_compte_inexistant_repond_401() throws Exception {
        Instant now = clock.instant();
        String token = buildToken(UUID.randomUUID(), now, now.plus(ACCESS_TOKEN_TTL), signingKey());

        mockMvc.perform(get("/api/account/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
