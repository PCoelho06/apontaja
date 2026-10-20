package com.apontaja.back.salon.application;

import java.util.List;

/** Valeurs par défaut et bornes des réglages de réservation en ligne (miroir des CHECK SQL). */
public final class BookingSettingsLimits {

    public static final boolean DEFAULT_ONLINE_BOOKING_ENABLED = false;
    public static final boolean DEFAULT_AUTO_CONFIRM = false;
    public static final int DEFAULT_SLOT_GRANULARITY_MINUTES = 15;
    public static final int DEFAULT_MIN_NOTICE_MINUTES = 120;
    public static final int DEFAULT_HORIZON_DAYS = 60;
    public static final int DEFAULT_CANCELLATION_DEADLINE_HOURS = 24;

    public static final List<Integer> ALLOWED_GRANULARITIES = List.of(5, 10, 15, 20, 30, 60);
    public static final int MAX_MIN_NOTICE_MINUTES = 10_080;
    public static final int MIN_HORIZON_DAYS = 1;
    public static final int MAX_HORIZON_DAYS = 365;
    public static final int MAX_CANCELLATION_DEADLINE_HOURS = 720;

    private BookingSettingsLimits() {
    }
}
