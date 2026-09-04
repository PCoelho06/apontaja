package com.apontaja.back.salon.application;

public class InvalidStaffRoleException extends RuntimeException {

    public InvalidStaffRoleException(String role) {
        super("Rôle invalide : " + role);
    }
}
