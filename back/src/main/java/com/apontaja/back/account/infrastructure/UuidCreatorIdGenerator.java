package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.IdGenerator;
import com.github.f4b6a3.uuid.UuidCreator;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class UuidCreatorIdGenerator implements IdGenerator {

    @Override
    public UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
