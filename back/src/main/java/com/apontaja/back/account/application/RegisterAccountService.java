package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.ConsentRecord;
import com.apontaja.back.account.domain.ConsentRecordRepository;
import com.apontaja.back.account.domain.ConsentType;
import com.apontaja.back.shared.domain.IdGenerator;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class RegisterAccountService {

    // Pas de gestion de plusieurs versions de CGU/politique de
    // confidentialité en v1 — une seule version active à la fois. À
    // externaliser en configuration si un vrai historique devient
    // nécessaire (re-consentement sur changement de version, etc.).
    private static final String TOS_VERSION = "v1";
    private static final String PRIVACY_VERSION = "v1";

    private final AccountRepository accountRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final IdGenerator idGenerator;
    private final Clock clock;

    RegisterAccountService(AccountRepository accountRepository, ConsentRecordRepository consentRecordRepository,
            EmailVerificationService emailVerificationService, PasswordEncoder passwordEncoder, IdGenerator idGenerator,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.consentRecordRepository = consentRecordRepository;
        this.emailVerificationService = emailVerificationService;
        this.passwordEncoder = passwordEncoder;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public RegisterAccountResult register(RegisterAccountCommand command) {
        String email = command.email().trim();

        if (accountRepository.existsAliveByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        Instant now = clock.instant();
        Account account = new Account(idGenerator.generate(), email, passwordEncoder.encode(command.rawPassword()),
                now);
        accountRepository.save(account);

        consentRecordRepository
                .save(new ConsentRecord(idGenerator.generate(), account.getId(), ConsentType.TOS, TOS_VERSION, now));
        consentRecordRepository.save(
                new ConsentRecord(idGenerator.generate(), account.getId(), ConsentType.PRIVACY, PRIVACY_VERSION, now));

        emailVerificationService.issueAndSend(account);

        return new RegisterAccountResult(account.getId(), account.getEmail());
    }
}
