package com.apontaja.back.resource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Plage horaire récurrente hebdomadaire d'un salon OU d'une ressource (jamais
 * les deux, cf. CHECK {@code ck_schedule_owner_xor}). Les heures sont des
 * heures locales du salon ({@link LocalTime}, pas des instants). Une plage ne
 * traverse pas minuit (end &gt; start).
 *
 * <p>
 * Une Schedule de ressource se résout toujours via Resource → Salon pour son
 * périmètre réel. Implémente {@link Persistable} comme les autres entités.
 */
@Entity
@Table(name = "schedule")
public class Schedule implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "salon_id", updatable = false)
    private UUID salonId;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeekCode;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Schedule() {
        // requis par Hibernate
    }

    private Schedule(UUID id, UUID salonId, UUID resourceId, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalTime endTime, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.salonId = salonId;
        this.resourceId = resourceId;
        this.dayOfWeekCode = DayOfWeekCodes.toCode(Objects.requireNonNull(dayOfWeek, "dayOfWeek"));
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("La fin doit être postérieure au début");
        }
    }

    public static Schedule forSalon(UUID id, UUID salonId, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalTime endTime, Instant createdAt) {
        return new Schedule(id, Objects.requireNonNull(salonId, "salonId"), null, dayOfWeek, startTime, endTime,
                createdAt);
    }

    public static Schedule forResource(UUID id, UUID resourceId, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalTime endTime, Instant createdAt) {
        return new Schedule(id, null, Objects.requireNonNull(resourceId, "resourceId"), dayOfWeek, startTime,
                endTime, createdAt);
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

    public UUID getSalonId() {
        return salonId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public DayOfWeek getDayOfWeek() {
        return DayOfWeekCodes.fromCode(dayOfWeekCode);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
