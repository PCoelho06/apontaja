package com.apontaja.back.salon.web;

import com.apontaja.back.salon.application.BookingSettings;

record BookingSettingsResponse(boolean onlineBookingEnabled, boolean autoConfirm, int slotGranularityMinutes,
        int minNoticeMinutes, int horizonDays, int cancellationDeadlineHours) {

    static BookingSettingsResponse from(BookingSettings s) {
        return new BookingSettingsResponse(s.onlineBookingEnabled(), s.autoConfirm(), s.slotGranularityMinutes(),
                s.minNoticeMinutes(), s.horizonDays(), s.cancellationDeadlineHours());
    }
}
