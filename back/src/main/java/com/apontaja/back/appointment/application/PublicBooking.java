package com.apontaja.back.appointment.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vues publiques de la réservation en ligne (sérialisées telles quelles par le contrôleur public).
 * Jamais d'identité de client, de compte ou de membre du staff : une ressource n'est exposée que par
 * son identifiant et le libellé choisi par le salon.
 */
public final class PublicBooking {

    private PublicBooking() {
    }

    public record SalonInfo(UUID salonId, String name, String address, String postalCode, String city,
            String country, String phone, String timezone, boolean autoConfirm, int minNoticeMinutes,
            int horizonDays, int cancellationDeadlineHours) {
    }

    public record Resource(UUID resourceId, String name, int priceCents, int durationMinutes) {
    }

    public record Offering(UUID serviceId, String name, String description, int defaultDurationMinutes,
            int defaultPriceCents, List<Resource> resources) {
    }

    /** Un début de créneau et les ressources qui peuvent le prendre (au moins une). */
    public record Slot(Instant startAt, List<UUID> resourceIds) {
    }

    /** Créneaux d'une date locale du salon (les jours sans créneau sont omis). */
    public record Day(String date, List<Slot> slots) {
    }

    public record Slots(UUID serviceId, String timezone, List<Resource> resources, List<Day> days) {
    }
}
