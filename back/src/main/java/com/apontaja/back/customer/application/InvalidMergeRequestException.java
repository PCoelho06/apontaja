package com.apontaja.back.customer.application;

/** Demande de fusion incohérente (liste vide, trop longue, survivant parmi les absorbés). */
public class InvalidMergeRequestException extends RuntimeException {

    public InvalidMergeRequestException(String message) {
        super(message);
    }
}
