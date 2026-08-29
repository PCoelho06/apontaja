package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpaRepository;

    AccountRepositoryAdapter(AccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Account save(Account account) {
        return jpaRepository.save(account);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Account> findAliveByEmail(String email) {
        return jpaRepository.findAliveByEmail(email);
    }

    @Override
    public boolean existsAliveByEmail(String email) {
        return jpaRepository.existsAliveByEmail(email);
    }
}
