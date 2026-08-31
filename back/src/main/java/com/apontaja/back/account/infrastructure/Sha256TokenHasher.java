package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.TokenHasher;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
class Sha256TokenHasher implements TokenHasher {

    @Override
    public String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 fait partie de toute JVM standard — ne devrait jamais arriver.
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}
