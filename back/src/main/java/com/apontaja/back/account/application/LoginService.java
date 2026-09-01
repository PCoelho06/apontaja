package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.IdGenerator;
import com.apontaja.back.account.domain.OpaqueTokenGenerator;
import com.apontaja.back.account.domain.RefreshToken;
import com.apontaja.back.account.domain.RefreshTokenRepository;
import com.apontaja.back.account.domain.TokenHasher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoginService {

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final TokenHasher tokenHasher;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    /**
     * Hash factice précalculé une fois : sert à faire tourner
     * passwordEncoder.matches() même quand le compte n'existe pas, pour ne
     * pas laisser fuiter par le timing de réponse si un email est déjà pris.
     */
    private final String dummyHash;

    LoginService(
            AccountRepository accountRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer,
            OpaqueTokenGenerator opaqueTokenGenerator,
            TokenHasher tokenHasher,
            IdGenerator idGenerator,
            Clock clock,
            @Value("${apontaja.security.refresh-token.ttl:30d}") Duration refreshTokenTtl) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.opaqueTokenGenerator = opaqueTokenGenerator;
        this.tokenHasher = tokenHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        Optional<Account> maybeAccount = accountRepository.findAliveByEmail(command.email().trim());

        boolean passwordMatches = passwordEncoder.matches(
                command.rawPassword(),
                maybeAccount.map(Account::getPasswordHash).orElse(dummyHash));

        if (maybeAccount.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        Account account = maybeAccount.get();

        String accessToken = accessTokenIssuer.issue(account.getId());

        String rawRefreshToken = opaqueTokenGenerator.generate();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(refreshTokenTtl);
        RefreshToken refreshToken = new RefreshToken(
                idGenerator.generate(),
                account.getId(),
                tokenHasher.hash(rawRefreshToken),
                command.deviceInfo(),
                expiresAt,
                now);
        refreshTokenRepository.save(refreshToken);

        return new LoginResult(account.getId(), account.getEmail(), accessToken, rawRefreshToken, expiresAt);
    }
}
