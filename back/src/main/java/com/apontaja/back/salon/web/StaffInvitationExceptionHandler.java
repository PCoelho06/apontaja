package com.apontaja.back.salon.web;

import com.apontaja.back.salon.application.AccountAlreadyStaffMemberException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice 
class StaffInvitationExceptionHandler {

    @ExceptionHandler (AccountAlreadyStaffMemberException.class)
    ResponseEntity<ProblemDetail> handleAlreadyMember(AccountAlreadyStaffMemberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()));
    }
}
