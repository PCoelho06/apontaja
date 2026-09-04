package com.apontaja.back.shared.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRandomOpaqueTokenGeneratorTest {

    @Test
    void genere_des_tokens_uniques_et_suffisamment_longs() {
        SecureRandomOpaqueTokenGenerator generator = new SecureRandomOpaqueTokenGenerator();

        long distinctCount = Stream.generate(generator::generate).limit(1000).distinct().count();

        assertThat(distinctCount).isEqualTo(1000);
        assertThat(generator.generate().length()).isGreaterThanOrEqualTo(40);
    }
}
