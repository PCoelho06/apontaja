package com.apontaja.back.service.application;

public class ServiceNameAlreadyUsedException extends RuntimeException {

    public ServiceNameAlreadyUsedException() {
        super("Une prestation porte déjà ce nom dans ce salon.");
    }
}
