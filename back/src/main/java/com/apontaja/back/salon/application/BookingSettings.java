package com.apontaja.back.salon.application;

import com.apontaja.back.salon.domain.SalonBookingSettings;

import java.util.UUID;

public record BookingSettings(UUID salonId, boolean onlineBookingEnabled, boolean autoConfirm,
        int slotGranularityMinutes, int minNoticeMinutes, int horizonDays, int cancellationDeadlineHours) {

    /** Réglages d'un salon qui n'a encore rien configuré : réservation en ligne désactivée. */
    public static BookingSettings defaults(UUID salonId) {
        return new BookingSettings(salonId, BookingSettingsLimits.DEFAULT_ONLINE_BOOKING_ENABLED,
                BookingSettingsLimits.DEFAULT_AUTO_CONFIRM, BookingSettingsLimits.DEFAULT_SLOT_GRANULARITY_MINUTES,
                BookingSettingsLimits.DEFAULT_MIN_NOTICE_MINUTES, BookingSettingsLimits.DEFAULT_HORIZON_DAYS,
                BookingSettingsLimits.DEFAULT_CANCELLATION_DEADLINE_HOURS);
    }

    static BookingSettings from(SalonBookingSettings s) {
        return new BookingSettings(s.getSalonId(), s.isOnlineBookingEnabled(), s.isAutoConfirm(),
                s.getSlotGranularityMinutes(), s.getMinNoticeMinutes(), s.getHorizonDays(),
                s.getCancellationDeadlineHours());
    }
}
