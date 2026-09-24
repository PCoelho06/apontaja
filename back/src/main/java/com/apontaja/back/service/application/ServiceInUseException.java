package com.apontaja.back.service.application;

public class ServiceInUseException extends RuntimeException {

    public ServiceInUseException() {
        super("Suppression impossible : des rendez-vous actifs sont rattachés à cette prestation.");
    }
}
