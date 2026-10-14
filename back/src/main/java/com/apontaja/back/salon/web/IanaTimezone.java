package com.apontaja.back.salon.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifiant de fuseau horaire IANA connu de la JVM (ex. Europe/Paris). Une valeur nulle ou
 * vide est laissée à {@code @NotBlank}, pour ne pas produire deux messages.
 */
@Documented
@Constraint(validatedBy = IanaTimezoneValidator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface IanaTimezone {

    String message() default "Fuseau horaire invalide (ex. Europe/Paris).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
