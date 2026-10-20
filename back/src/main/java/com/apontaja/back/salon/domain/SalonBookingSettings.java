package com.apontaja.back.salon.domain;

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
 * Réglages de réservation en ligne d'un salon (table {@code salon_booking_settings}, clé = salon).
 * Une ligne absente signifie « valeurs par défaut, réservation désactivée » (voir
 * {@code BookingSettingsLimits}). Mutateur inconditionnel : les bornes sont vérifiées par le service.
 *
 * <p>
 * Implémente {@link Persistable} : l'identifiant (le salon) est fourni par l'application.
 */
@Entity
@Table(name = "salon_booking_settings")
public class SalonBookingSettings implements Persistable<UUID> {

    @Id
    @Column(name = "salon_id")
    private UUID salonId;

    @Column(name = "online_booking_enabled", nullable = false)
    private boolean onlineBookingEnabled;

    @Column(name = "auto_confirm", nullable = false)
    private boolean autoConfirm;

    @Column(name = "slot_granularity_minutes", nullable = false)
    private int slotGranularityMinutes;

    @Column(name = "min_notice_minutes", nullable = false)
    private int minNoticeMinutes;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "cancellation_deadline_hours", nullable = false)
    private int cancellationDeadlineHours;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    protected SalonBookingSettings() {
        // requis par Hibernate
    }

    public SalonBookingSettings(UUID salonId, boolean onlineBookingEnabled, boolean autoConfirm,
            int slotGranularityMinutes, int minNoticeMinutes, int horizonDays, int cancellationDeadlineHours,
            Instant updatedAt) {
        this.salonId = Objects.requireNonNull(salonId, "salonId");
        apply(onlineBookingEnabled, autoConfirm, slotGranularityMinutes, minNoticeMinutes, horizonDays,
                cancellationDeadlineHours, updatedAt);
    }

    public void apply(boolean onlineBookingEnabled, boolean autoConfirm, int slotGranularityMinutes,
            int minNoticeMinutes, int horizonDays, int cancellationDeadlineHours, Instant updatedAt) {
        this.onlineBookingEnabled = onlineBookingEnabled;
        this.autoConfirm = autoConfirm;
        this.slotGranularityMinutes = slotGranularityMinutes;
        this.minNoticeMinutes = minNoticeMinutes;
        this.horizonDays = horizonDays;
        this.cancellationDeadlineHours = cancellationDeadlineHours;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override
    public UUID getId() {
        return salonId;
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

    public UUID getSalonId() {
        return salonId;
    }

    public boolean isOnlineBookingEnabled() {
        return onlineBookingEnabled;
    }

    public boolean isAutoConfirm() {
        return autoConfirm;
    }

    public int getSlotGranularityMinutes() {
        return slotGranularityMinutes;
    }

    public int getMinNoticeMinutes() {
        return minNoticeMinutes;
    }

    public int getHorizonDays() {
        return horizonDays;
    }

    public int getCancellationDeadlineHours() {
        return cancellationDeadlineHours;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
