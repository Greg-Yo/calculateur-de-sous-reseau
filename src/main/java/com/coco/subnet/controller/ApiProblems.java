package com.coco.subnet.controller;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public static ResponseEntity<ProblemDetail> payloadTooLarge() {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Requête trop volumineuse", ErrorCode.PAYLOAD_TOO_LARGE,
                "Le corps de la requête dépasse 64 Ko", List.of(), new HttpHeaders());
    }

    /** Erreurs qui surviennent pendant le choix du controller. */
    public static ResponseEntity<ProblemDetail> routing(Exception ex) {
        if (ex instanceof HttpRequestMethodNotSupportedException methodEx) {
            HttpHeaders headers = new HttpHeaders();
            Set<HttpMethod> allowed = methodEx.getSupportedHttpMethods();
            if (allowed != null) {
                headers.setAllow(allowed);
            }
            return build(HttpStatus.METHOD_NOT_ALLOWED, "Méthode non autorisée", ErrorCode.METHOD_NOT_ALLOWED,
                    "La méthode " + methodEx.getMethod() + " n'est pas autorisée sur cette URL", List.of(), headers);
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Format de requête non supporté", ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Le corps de la requête doit être en JSON (Content-Type: application/json)", List.of(), new HttpHeaders());
        }
        if (ex instanceof HttpMediaTypeNotAcceptableException) {
            return build(HttpStatus.NOT_ACCEPTABLE, "Format de réponse non disponible", ErrorCode.NOT_ACCEPTABLE,
                    "Le format demandé dans l'en-tête Accept n'est pas disponible", List.of(), new HttpHeaders());
        }
        return build(HttpStatus.NOT_FOUND, "Ressource introuvable", ErrorCode.NOT_FOUND,
                "Aucune ressource à cette adresse", List.of(), new HttpHeaders());
    }

    /** Écrit la réponse à la main : utilisé là où Spring MVC ne peut pas le faire (filtre, avant le routage). */
    public static void write(ResponseEntity<ProblemDetail> entity, HttpServletResponse response, ObjectMapper mapper)
            throws IOException {
        response.setStatus(entity.getStatusCode().value());
        entity.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), toJson(entity.getBody()));
    }

    private static Map<String, Object> toJson(ProblemDetail problem) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", problem.getType().toString());
        json.put("title", problem.getTitle());
        json.put("status", problem.getStatus());
        json.put("detail", problem.getDetail());
        json.putAll(problem.getProperties());
        return json;
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
