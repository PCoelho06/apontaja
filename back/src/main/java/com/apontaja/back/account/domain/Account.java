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
 * Identité du compte : identifiants (email, mot de passe) et nom (prénom, nom).
 * Voir la migration V1 pour la définition canonique (table {@code account}) :
 * l'unicité d'email n'est garantie qu'entre comptes "vivants" (index unique
 * partiel {@code WHERE deleted_at IS NULL} en base) — cette classe ne recalcule
 * pas cette règle, elle est appliquée par la contrainte DB et vérifiée en amont
 * via {@link AccountRepository#existsAliveByEmail(String)}.
 *
 * <p>
 * Implémente {@link Persistable} car l'ID (UUIDv7) est assigné côté application
 * avant l'appel à {@code save()} — sans ça, Spring Data JPA déduit à tort que
 * l'entité n'est "pas nouvelle" (ID non null) et fait un {@code merge()} au
 * lieu d'un {@code persist()} (SELECT superflu, identité d'objet différente
 * après sauvegarde).
 */
@Entity
@Table(name = "account")
public class Account implements Persistable<UUID> {

    static final int MAX_NAME_LENGTH = 100;

    @Id
    private UUID id;

    /**
     * Colonne citext en base : comparaison insensible à la casse déléguée à
     * PostgreSQL.
     */
    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

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

    public Account(UUID id, String email, String firstName, String lastName, String passwordHash, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.firstName = requireName(firstName, "firstName");
        this.lastName = requireName(lastName, "lastName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Nom saisi par l'utilisateur : espaces autour retirés, ni vide ni plus long
     * que MAX_NAME_LENGTH.
     */
    private static String requireName(String value, String field) {
        String trimmed = Objects.requireNonNull(value, field).trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(field + " invalide : 1 à " + MAX_NAME_LENGTH + " caractères attendus");
        }
        return trimmed;
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

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
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

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
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
