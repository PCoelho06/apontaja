package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.AccountToken;
import com.apontaja.back.account.domain.AccountTokenRepository;
import com.apontaja.back.account.domain.AccountTokenType;
import com.apontaja.back.account.domain.EmailSender;
import com.apontaja.back.account.domain.IdGenerator;
import com.apontaja.back.account.domain.OpaqueTokenGenerator;
import com.apontaja.back.account.domain.RefreshTokenRepository;
import com.apontaja.back.account.domain.TokenHasher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class PasswordResetService {

    private final AccountRepository accountRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final TokenHasher tokenHasher;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final Duration tokenTtl;
    private final String frontendBaseUrl;

    PasswordResetService(
            AccountRepository accountRepository,
            AccountTokenRepository accountTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            OpaqueTokenGenerator opaqueTokenGenerator,
            TokenHasher tokenHasher,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            IdGenerator idGenerator,
            Clock clock,
            // Fenêtre volontairement courte : plus sensible qu'une vérification d'email.
            @Value("${apontaja.security.password-reset.ttl:1h}") Duration tokenTtl,
            @Value("${apontaja.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.accountRepository = accountRepository;
        this.accountTokenRepository = accountTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.opaqueTokenGenerator = opaqueTokenGenerator;
        this.tokenHasher = tokenHasher;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Toujours un succès silencieux côté appelant — anti-énumération (même principe que login). */
    @Transactional
    public void requestReset(String email) {
        accountRepository.findAliveByEmail(email.trim()).ifPresent(account -> {
            Instant now = clock.instant();
            String rawToken = opaqueTokenGenerator.generate();

            AccountToken token = new AccountToken(
                    idGenerator.generate(),
                    account.getId(),
                    AccountTokenType.PASSWORD_RESET,
                    tokenHasher.hash(rawToken),
                    now.plus(tokenTtl),
                    now);
            accountTokenRepository.save(token);

            String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
            emailSender.send(
                    account.getEmail(),
                    "Réinitialisation de votre mot de passe",
                    "Cliquez sur ce lien pour choisir un nouveau mot de passe (valable "
                            + tokenTtl.toMinutes() + " min) : " + link);
        });
    }

    /**
     * Un reset réussi révoque toutes les sessions actives du compte : un mot
     * de passe qu'on réinitialise est un mot de passe qu'on soupçonne
     * compromis (même principe que la détection de réutilisation, tranche 5).
     */
    @Transactional
    public void resetPassword(String rawToken, String newRawPassword) {
        AccountToken token = accountTokenRepository.findByTokenHash(tokenHasher.hash(rawToken))
                .filter(t -> t.getType() == AccountTokenType.PASSWORD_RESET)
                .orElseThrow(InvalidOrExpiredTokenException::new);

        Instant now = clock.instant();
        if (!token.isUsable(now)) {
            throw new InvalidOrExpiredTokenException();
        }

        Account account = accountRepository.findById(token.getAccountId())
                .orElseThrow(InvalidOrExpiredTokenException::new);

        account.changePassword(passwordEncoder.encode(newRawPassword));
        accountRepository.save(account);

        token.markUsed(now);
        accountTokenRepository.save(token);

        refreshTokenRepository.findActiveByAccountId(account.getId())
                .forEach(refreshToken -> {
                    refreshToken.revoke(now);
                    refreshTokenRepository.save(refreshToken);
                });
    }
}
