package com.apontaja.back.salon.application;

public class AccountAlreadyStaffMemberException extends RuntimeException {

    public AccountAlreadyStaffMemberException() {
        super("Ce compte fait déjà partie du staff de ce salon.");
    }
}
