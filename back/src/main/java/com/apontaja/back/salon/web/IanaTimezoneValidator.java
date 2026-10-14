package com.apontaja.back.salon.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.ZoneId;

/**
 * Accepte un identifiant de région connu de la JVM, à l'identique : ni décalage seul
 * ("+02:00"), ni espaces autour, ni casse différente. La valeur est enregistrée telle quelle
 * sur le salon et relue par {@code ZoneId.of(...)} (disponibilités, agenda).
 */
public class IanaTimezoneValidator implements ConstraintValidator<IanaTimezone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || ZoneId.getAvailableZoneIds().contains(value);
    }
}
