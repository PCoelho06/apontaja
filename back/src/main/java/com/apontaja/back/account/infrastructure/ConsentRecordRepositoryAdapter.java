package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.ConsentRecord;
import com.apontaja.back.account.domain.ConsentRecordRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class ConsentRecordRepositoryAdapter implements ConsentRecordRepository {

    private final ConsentRecordJpaRepository jpaRepository;

    ConsentRecordRepositoryAdapter(ConsentRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ConsentRecord save(ConsentRecord consentRecord) {
        return jpaRepository.save(consentRecord);
    }

    @Override
    public List<ConsentRecord> findByAccountId(UUID accountId) {
        return jpaRepository.findByAccountId(accountId);
    }
}
