package com.apontaja.back.appointment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Rendez-vous, snapshot prix/durée au moment de la réservation. La ressource
 * réservée n'est PAS stockée ici (v1 = toujours une seule ligne dans
 * {@code appointment_resource}, cf. {@link AppointmentResource}) — le modèle
 * supporte plusieurs ressources par conception, non exploité en v1.
 *
 * <p>
 * Colonnes {@code cancelled_at}/{@code cancelled_by}/{@code
 * cancellation_reason} du schéma volontairement non mappées ici : rien ne les
 * renseigne avant la Phase 3 tranche 7 (annulation). Aucune mutation exposée
 * en tranche 6 (création uniquement) — {@code status} est toujours
 * {@code SCHEDULED} à la construction.
 *
 * <p>
 * Implémente {@link Persistable} pour la même raison que {@code Account}.
 */
@Entity
@Table(name = "appointment")
public class Appointment implements Persistable<UUID> {

    private static final int MAX_DURATION_MINUTES = 1440;

    @Id
    private UUID id;

    @Column(name = "salon_id", nullable = false, updatable = false)
    private UUID salonId;

    @Column(name = "customer_profile_id", nullable = false, updatable = false)
    private UUID customerProfileId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "price_at_booking_cents", nullable = false)
    private int priceAtBookingCents;

    @Column(name = "duration_at_booking_minutes", nullable = false)
    private int durationAtBookingMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    private boolean isNew = true;

    protected Appointment() {
        // requis par Hibernate
    }

    public Appointment(UUID id, UUID salonId, UUID customerProfileId, UUID serviceId, Instant startAt, Instant endAt,
            int priceAtBookingCents, int durationAtBookingMinutes, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.salonId = Objects.requireNonNull(salonId, "salonId");
        this.customerProfileId = Objects.requireNonNull(customerProfileId, "customerProfileId");
        this.serviceId = Objects.requireNonNull(serviceId, "serviceId");
        this.startAt = Objects.requireNonNull(startAt, "startAt");
        this.endAt = Objects.requireNonNull(endAt, "endAt");
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("La fin doit être postérieure au début");
        }
        if (priceAtBookingCents < 0) {
            throw new IllegalArgumentException("Prix invalide : " + priceAtBookingCents);
        }
        if (durationAtBookingMinutes < 1 || durationAtBookingMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("Durée invalide : " + durationAtBookingMinutes);
        }
        this.priceAtBookingCents = priceAtBookingCents;
        this.durationAtBookingMinutes = durationAtBookingMinutes;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = AppointmentStatus.SCHEDULED;
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

    public UUID getSalonId() {
        return salonId;
    }

    public UUID getCustomerProfileId() {
        return customerProfileId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public int getPriceAtBookingCents() {
        return priceAtBookingCents;
    }

    public int getDurationAtBookingMinutes() {
        return durationAtBookingMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
