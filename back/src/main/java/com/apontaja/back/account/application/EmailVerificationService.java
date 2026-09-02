package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.AccountToken;
import com.apontaja.back.account.domain.AccountTokenRepository;
import com.apontaja.back.account.domain.AccountTokenType;
import com.apontaja.back.account.domain.EmailSender;
import com.apontaja.back.account.domain.IdGenerator;
import com.apontaja.back.account.domain.OpaqueTokenGenerator;
import com.apontaja.back.account.domain.TokenHasher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class EmailVerificationService {

    private final AccountRepository accountRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final TokenHasher tokenHasher;
    private final EmailSender emailSender;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final Duration tokenTtl;
    private final String frontendBaseUrl;

    EmailVerificationService(
            AccountRepository accountRepository,
            AccountTokenRepository accountTokenRepository,
            OpaqueTokenGenerator opaqueTokenGenerator,
            TokenHasher tokenHasher,
            EmailSender emailSender,
            IdGenerator idGenerator,
            Clock clock,
            @Value("${apontaja.security.email-verification.ttl:48h}") Duration tokenTtl,
            // ADAPTER cette valeur par défaut si le port dev du portail-salon diffère
            // (5173 = défaut Vite). Pas de vraie décision de prod ici (§6 [OPEN]).
            @Value("${apontaja.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.accountRepository = accountRepository;
        this.accountTokenRepository = accountTokenRepository;
        this.opaqueTokenGenerator = opaqueTokenGenerator;
        this.tokenHasher = tokenHasher;
        this.emailSender = emailSender;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Appelé automatiquement à l'inscription (RegisterAccountService) et par resend(). */
    @Transactional
    public void issueAndSend(Account account) {
        Instant now = clock.instant();
        String rawToken = opaqueTokenGenerator.generate();

        AccountToken token = new AccountToken(
                idGenerator.generate(),
                account.getId(),
                AccountTokenType.EMAIL_VERIFICATION,
                tokenHasher.hash(rawToken),
                now.plus(tokenTtl),
                now);
        accountTokenRepository.save(token);

        String link = frontendBaseUrl + "/confirmer-email?token=" + rawToken;
        emailSender.send(
                account.getEmail(),
                "Confirmez votre adresse email",
                "Cliquez sur ce lien pour confirmer votre email (valable "
                        + tokenTtl.toHours() + "h) : " + link);
    }

    @Transactional
    public void confirm(String rawToken) {
        AccountToken token = accountTokenRepository.findByTokenHash(tokenHasher.hash(rawToken))
                .filter(t -> t.getType() == AccountTokenType.EMAIL_VERIFICATION)
                .orElseThrow(InvalidOrExpiredTokenException::new);

        Instant now = clock.instant();
        if (!token.isUsable(now)) {
            throw new InvalidOrExpiredTokenException();
        }

        Account account = accountRepository.findById(token.getAccountId())
                .orElseThrow(InvalidOrExpiredTokenException::new);

        account.markEmailVerified(now);
        accountRepository.save(account);

        token.markUsed(now);
        accountTokenRepository.save(token);
    }

    /**
     * Toujours un succès silencieux côté appelant, que l'email existe, soit
     * déjà vérifié, ou non — anti-énumération (même principe que login/
     * forgot-password).
     */
    @Transactional
    public void resend(String email) {
        accountRepository.findAliveByEmail(email.trim())
                .filter(account -> !account.isEmailVerified())
                .ifPresent(this::issueAndSend);
    }
}
