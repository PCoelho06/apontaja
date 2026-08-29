package com.apontaja.back.account.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidCreatorIdGeneratorIT {

    @Test
    void genere_des_uuid_en_version_7() {
        UuidCreatorIdGenerator generator = new UuidCreatorIdGenerator();

        UUID id = generator.generate();

        assertThat(id.version()).isEqualTo(7);
    }
}
