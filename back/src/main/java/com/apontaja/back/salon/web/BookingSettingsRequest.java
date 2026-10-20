package com.apontaja.back.salon.web;

import jakarta.validation.constraints.NotNull;

/** Remplacement complet : tous les champs sont obligatoires (les bornes sont vérifiées par le service). */
record BookingSettingsRequest(@NotNull Boolean onlineBookingEnabled, @NotNull Boolean autoConfirm,
        @NotNull Integer slotGranularityMinutes, @NotNull Integer minNoticeMinutes, @NotNull Integer horizonDays,
        @NotNull Integer cancellationDeadlineHours) {
}
