package com.coco.subnet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Erreurs levées avant qu'un controller soit choisi : un advice limité à ApiController ne les verrait pas.
 * Sous /api : JSON. Pages web : redirection (mauvaise méthode) ou page d'erreur de Spring Boot.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class RoutingErrorHandler {

    private final ObjectMapper objectMapper;

    public RoutingErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class, HttpMediaTypeNotSupportedException.class})
    public void handle(Exception ex, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/")) {
            ApiProblems.write(ApiProblems.routing(ex), response, objectMapper);
            return;
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            response.sendRedirect(request.getContextPath() + redirectTarget(path));
            return;
        }
        // Relancer : Spring Boot affiche alors templates/error/404.html
        throw ex;
    }

    /** Mauvaise méthode : retour à la page dont l'URL est la plus proche. */
    static String redirectTarget(String path) {
        if (path.startsWith("/ipv6/plan")) {
            return "/ipv6/plan";
        }
        if (path.startsWith("/ipv6")) {
            return "/ipv6";
        }
        return path.startsWith("/vlsm") ? "/vlsm" : "/";
    }
}
