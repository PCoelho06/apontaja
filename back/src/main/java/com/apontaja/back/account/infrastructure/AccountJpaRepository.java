package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.Account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface AccountJpaRepository extends JpaRepository<Account, UUID> {

    @Query("SELECT a FROM Account a WHERE a.email = :email AND a.deletedAt IS NULL")
    Optional<Account> findAliveByEmail(@Param("email") String email);

    @Query("SELECT COUNT(a) > 0 FROM Account a WHERE a.email = :email AND a.deletedAt IS NULL")
    boolean existsAliveByEmail(@Param("email") String email);
}
