package com.apontaja.back.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;

class ApiProblemsTest {

    @Test
    void ajoute_le_code_le_statut_et_le_detail() {
        ResponseEntity<ProblemDetail> response = ApiProblems.of(HttpStatus.CONFLICT, "Déjà pris.",
                ApiErrorCode.RESOURCE_NAME_ALREADY_USED);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Déjà pris.");
        assertThat(response.getBody().getProperties()).containsEntry("code", "RESOURCE_NAME_ALREADY_USED");
    }

    @Test
    void problem_permet_d_ajouter_d_autres_proprietes() {
        ProblemDetail problem = ApiProblems.problem(HttpStatus.CONFLICT, "x",
                ApiErrorCode.CLOSURE_BLOCKED_BY_APPOINTMENTS);
        problem.setProperty("count", 2);

        assertThat(problem.getProperties()).containsEntry("code", "CLOSURE_BLOCKED_BY_APPOINTMENTS")
                .containsEntry("count", 2);
    }

    @Test
    void les_codes_sont_en_majuscules_avec_underscores() {
        assertThat(Arrays.stream(ApiErrorCode.values()).map(Enum::name)).allMatch(n -> n.matches("[A-Z][A-Z0-9_]*"));
    }
}
