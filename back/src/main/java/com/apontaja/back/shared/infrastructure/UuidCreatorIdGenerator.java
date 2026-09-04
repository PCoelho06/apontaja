package com.apontaja.back.shared.infrastructure;

import com.apontaja.back.shared.domain.IdGenerator;
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
