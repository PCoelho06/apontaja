package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.OpaqueTokenGenerator;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
class SecureRandomOpaqueTokenGenerator implements OpaqueTokenGenerator {

    private static final int TOKEN_BYTES = 32; // 256 bits

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
