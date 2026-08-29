package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.ConsentRecord;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ConsentRecordJpaRepository extends JpaRepository<ConsentRecord, UUID> {

    List<ConsentRecord> findByAccountId(UUID accountId);
}
