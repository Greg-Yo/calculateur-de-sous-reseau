package com.coco.subnet.controller;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InvalidInputException;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException ex) {
        return ApiProblems.badRequest(ValidationErrors.from(ex.getBindingResult()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> missingParameter(MissingServletRequestParameterException ex) {
        return ApiProblems.badRequest(ErrorCode.MISSING_PARAMETER, ex.getParameterName(),
                "Paramètre obligatoire manquant : " + ex.getParameterName());
    }

    /** Du plus précis au plus général : UnrecognizedPropertyException hérite de MismatchedInputException. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadableBody(HttpMessageNotReadableException ex) {
        UnrecognizedPropertyException unknown = findCause(ex, UnrecognizedPropertyException.class);
        if (unknown != null) {
            return ApiProblems.badRequest(ErrorCode.UNKNOWN_FIELD, path(unknown), "Champ inconnu : " + unknown.getPropertyName());
        }
        MismatchedInputException mismatch = findCause(ex, MismatchedInputException.class);
        if (mismatch != null) {
            String field = path(mismatch);
            return ApiProblems.badRequest(ErrorCode.TYPE_MISMATCH, field, "Type invalide pour le champ « " + field + " »");
        }
        if (findCause(ex, InputCoercionException.class) != null) {
            return ApiProblems.badRequest(ErrorCode.TYPE_MISMATCH, null, "Nombre hors limites dans la requête");
        }
        if (ex.getMessage() != null && ex.getMessage().startsWith("Required request body is missing")) {
            return ApiProblems.badRequest(ErrorCode.MISSING_BODY, null, "Le corps de la requête est obligatoire");
        }
        return ApiProblems.badRequest(ErrorCode.MALFORMED_JSON, null, "JSON mal formé");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("Erreur inattendue dans l'API", ex);
        return ApiProblems.internalError();
    }

    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    /** Chemin Jackson → notation des champs, par exemple vlans[0].hosts. */
    private static String path(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
