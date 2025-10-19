package com.apontaja.backend.service;

import com.apontaja.backend.model.RefreshToken;
import com.apontaja.backend.model.User;

import java.util.Optional;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(User user);

    Optional<RefreshToken> findByToken(String token);

    RefreshToken verifyExpiration(RefreshToken token);

    void deleteByUser(User user);

    void deleteToken(RefreshToken token);
}
