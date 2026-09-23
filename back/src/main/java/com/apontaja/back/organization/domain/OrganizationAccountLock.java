package com.apontaja.back.organization.domain;

import java.util.UUID;

public interface OrganizationAccountLock {

    void lock(UUID accountId);
}
