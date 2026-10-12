package com.coco.subnet.controller;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InvalidInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erreurs survenues une fois la requête arrivée dans ApiController. */
@RestControllerAdvice(assignableTypes = ApiController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ProblemDetail> invalidInput(InvalidInputException ex) {
        return ApiProblems.badRequest(ex.errors());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> missingParameter(MissingServletRequestParameterException ex) {
        return ApiProblems.badRequest(ErrorCode.MISSING_PARAMETER, ex.getParameterName(),
                "Paramètre obligatoire manquant : " + ex.getParameterName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Erreur inattendue dans l'API", ex);
        return ApiProblems.internalError();
    }
}
