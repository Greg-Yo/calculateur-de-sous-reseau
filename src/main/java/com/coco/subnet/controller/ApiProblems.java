package com.coco.subnet.controller;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.List;

/** Fabrique unique des réponses d'erreur de l'API (RFC 9457 + propriétés « code » et « errors »). */
public final class ApiProblems {

    private ApiProblems() {
    }

    public static ResponseEntity<ProblemDetail> badRequest(List<InputError> errors) {
        ErrorCode code = errors.size() == 1 ? errors.get(0).code() : ErrorCode.VALIDATION_FAILED;
        String detail = errors.size() == 1 ? errors.get(0).message() : errors.size() + " erreurs dans la requête";
        return build(HttpStatus.BAD_REQUEST, "Entrée invalide", code, detail, errors, new HttpHeaders());
    }

    public static ResponseEntity<ProblemDetail> badRequest(ErrorCode code, String field, String message) {
        return badRequest(List.of(new InputError(code, field, message)));
    }

    public static ResponseEntity<ProblemDetail> internalError() {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne", ErrorCode.INTERNAL_ERROR,
                "Une erreur interne est survenue", List.of(), new HttpHeaders());
    }

    public static ResponseEntity<ProblemDetail> build(HttpStatus status, String title, ErrorCode code, String detail,
                                                      List<InputError> errors, HttpHeaders headers) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code.name());
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
