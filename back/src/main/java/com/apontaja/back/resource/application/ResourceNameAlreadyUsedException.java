package com.apontaja.back.resource.application;

public class ResourceNameAlreadyUsedException extends RuntimeException {

    public ResourceNameAlreadyUsedException() {
        super("Une ressource porte déjà ce nom dans ce salon.");
    }
}
