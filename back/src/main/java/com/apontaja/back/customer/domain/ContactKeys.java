package com.apontaja.back.customer.domain;

import java.util.Locale;

/**
 * Clés de rapprochement des coordonnées. Fonctions pures, rien n'est stocké.
 *
 * <p>
 * Téléphone : on ne connaît pas le pays du salon depuis ce domaine, donc la clé est le « numéro significatif » :
 * les 9 derniers chiffres (« 06 12 34 56 78 » et « +33 6 12 34 56 78 » donnent la même clé). Moins de 8
 * chiffres : pas de clé, pour ne pas rapprocher des numéros trop courts.
 */
public final class ContactKeys {

    static final int MIN_PHONE_DIGITS = 8;
    static final int PHONE_KEY_LENGTH = 9;

    private ContactKeys() {
    }

    /** Email trimé en minuscules, {@code null} s'il est vide. */
    public static String emailKey(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Neuf derniers chiffres du téléphone, {@code null} s'il a moins de 8 chiffres. */
    public static String phoneKey(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < MIN_PHONE_DIGITS) {
            return null;
        }
        return digits.length() > PHONE_KEY_LENGTH ? digits.substring(digits.length() - PHONE_KEY_LENGTH) : digits;
    }
}
