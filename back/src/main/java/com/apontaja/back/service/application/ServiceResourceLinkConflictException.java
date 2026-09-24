package com.apontaja.back.service.application;

public class ServiceResourceLinkConflictException extends RuntimeException {

    public ServiceResourceLinkConflictException() {
        super("Association modifiée simultanément, réessayez.");
    }
}
