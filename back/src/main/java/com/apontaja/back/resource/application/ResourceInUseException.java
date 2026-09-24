package com.apontaja.back.resource.application;

public class ResourceInUseException extends RuntimeException {

    public ResourceInUseException() {
        super("Suppression impossible : des rendez-vous actifs sont rattachés à cette ressource.");
    }
}
