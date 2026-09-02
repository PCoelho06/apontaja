package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.AccountToken;
import com.apontaja.back.account.domain.AccountTokenRepository;
import com.apontaja.back.account.domain.AccountTokenType;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AccountTokenRepositoryAdapter implements AccountTokenRepository {

    private final AccountTokenJpaRepository jpaRepository;

    AccountTokenRepositoryAdapter(AccountTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AccountToken save(AccountToken token) {
        return jpaRepository.save(token);
    }

    @Override
    public Optional<AccountToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash);
    }

    @Override
    public List<AccountToken> findActiveByAccountIdAndType(UUID accountId, AccountTokenType type) {
        return jpaRepository.findActiveByAccountIdAndType(accountId, type);
    }
}
