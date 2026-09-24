package com.apontaja.back.resource.application;

public class InvalidResourceTypeException extends RuntimeException {

    public InvalidResourceTypeException(String type) {
        super("Type de ressource invalide : " + type);
    }
}
