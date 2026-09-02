package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.AccountRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AccountQueryService {

    private final AccountRepository accountRepository;

    AccountQueryService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** Vide si le compte n'existe pas ou a été soft-deleted — voir AccountController pour le rationnel. */
    public Optional<AccountSummary> findAliveById(UUID accountId) {
        return accountRepository.findById(accountId)
                .filter(account -> !account.isDeleted())
                .map(account -> new AccountSummary(account.getId(), account.getEmail(), account.isEmailVerified()));
    }
}
