package com.apontaja.back.account.domain;

import java.util.List;
import java.util.UUID;

public interface ConsentRecordRepository {

    ConsentRecord save(ConsentRecord consentRecord);

    List<ConsentRecord> findByAccountId(UUID accountId);
}
