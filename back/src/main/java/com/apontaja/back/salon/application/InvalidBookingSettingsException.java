package com.apontaja.back.salon.application;

public class InvalidBookingSettingsException extends RuntimeException {

    public InvalidBookingSettingsException(String message) {
        super(message);
    }
}
