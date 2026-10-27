package com.apontaja.back.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** Fabrique des réponses d'erreur métier : {@code ProblemDetail} avec la propriété {@code code}. */
public final class ApiProblems {

    public static final String CODE_PROPERTY = "code";

    private ApiProblems() {
    }

    public static ProblemDetail problem(HttpStatus status, String detail, ApiErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE_PROPERTY, code.name());
        return problem;
    }

    public static ResponseEntity<ProblemDetail> of(HttpStatus status, String detail, ApiErrorCode code) {
        return ResponseEntity.status(status).body(problem(status, detail, code));
    }
}
