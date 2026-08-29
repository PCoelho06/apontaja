package com.apontaja.back.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Session / device. {@code accountId} en UUID brut (pas de relation JPA
 * vers {@link Account}) volontairement : évite le lazy-loading sur le
 * chemin chaud de validation d'un refresh token (tranche 5).
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Jamais le token en clair — seul le hash est persisté. */
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // requis par Hibernate
    }

    public RefreshToken(
            UUID id,
            UUID accountId,
            String tokenHash,
            String deviceInfo,
            Instant expiresAt,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.deviceInfo = deviceInfo;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    public void revoke(Instant at) {
        this.revokedAt = Objects.requireNonNull(at, "at");
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
