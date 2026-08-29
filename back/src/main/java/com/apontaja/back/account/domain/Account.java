package com.apontaja.back.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Identité technique pure. Voir apontaja-schema.sql pour la définition
 * canonique (table {@code account}) : l'unicité d'email n'est garantie
 * qu'entre comptes "vivants" (index unique partiel {@code WHERE deleted_at
 * IS NULL} en base) — cette classe ne recalcule pas cette règle, elle est
 * appliquée par la contrainte DB et vérifiée en amont via
 * {@link AccountRepository#existsAliveByEmail(String)}.
 *
 * <p>
 * Implémente {@link Persistable} car l'ID (UUIDv7) est assigné côté
 * application avant l'appel à {@code save()} — sans ça, Spring Data JPA
 * déduit à tort que l'entité n'est "pas nouvelle" (ID non null) et fait un
 * {@code merge()} au lieu d'un {@code persist()} (SELECT superflu, identité
 * d'objet différente après sauvegarde).
 */
@Entity
@Table(name = "account")
public class Account implements Persistable<UUID> {

    @Id
    private UUID id;

    /**
     * Colonne citext en base : comparaison insensible à la casse déléguée à
     * PostgreSQL.
     */
    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    private boolean isNew = true;

    protected Account() {
        // requis par Hibernate
    }

    public Account(UUID id, String email, String passwordHash, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public void markEmailVerified(Instant at) {
        this.emailVerifiedAt = Objects.requireNonNull(at, "at");
    }

    public void softDelete(Instant at) {
        this.deletedAt = Objects.requireNonNull(at, "at");
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public String getEmail() {
        return email;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
