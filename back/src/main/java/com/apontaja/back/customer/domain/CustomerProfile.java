package com.apontaja.back.customer.domain;

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
 * Identité personne, "réclamable" par un compte (Phase 4). {@code accountId}
 * toujours {@code null} en Phase 3 (aucune auto-inscription possible tant que
 * le portail client n'existe pas) — champ en UUID brut, pas de relation JPA
 * vers {@code Account} (même principe que {@code StaffMembership.accountId}).
 *
 * <p>
 * Contrat obligatoire (CHECK {@code ck_customer_profile_has_contact}) : email
 * OU téléphone renseigné. Pas de suppression exposée en Phase 3 (report Phase
 * 4) — {@code deletedAt} mappé uniquement pour que les requêtes "alive"
 * filtrent déjà correctement.
 *
 * <p>
 * Implémente {@link Persistable} pour la même raison que {@code Account}.
 */
@Entity
@Table(name = "customer_profile")
public class CustomerProfile implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(columnDefinition = "citext")
    private String email;

    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    private boolean isNew = true;

    protected CustomerProfile() {
        // requis par Hibernate
    }

    public CustomerProfile(UUID id, String firstName, String lastName, String email, String phone,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.firstName = Objects.requireNonNull(firstName, "firstName");
        this.lastName = Objects.requireNonNull(lastName, "lastName");
        if (isBlank(email) && isBlank(phone)) {
            throw new IllegalArgumentException("Email ou téléphone requis.");
        }
        this.email = isBlank(email) ? null : email;
        this.phone = isBlank(phone) ? null : phone;
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

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
