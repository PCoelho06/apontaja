package com.apontaja.back.salon.application;

import java.util.UUID;

/** Remplacement complet des réglages. */
public record UpdateBookingSettingsCommand(UUID salonId, boolean onlineBookingEnabled, boolean autoConfirm,
        int slotGranularityMinutes, int minNoticeMinutes, int horizonDays, int cancellationDeadlineHours) {
}
