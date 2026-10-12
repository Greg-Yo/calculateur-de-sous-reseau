package com.coco.subnet.model;

import java.util.List;

/** Seule exception métier du projet : elle porte une ou plusieurs erreurs de saisie. */
public class InvalidInputException extends RuntimeException {

    private final List<InputError> errors;

    public InvalidInputException(List<InputError> errors) {
        super(firstMessage(errors));
        this.errors = List.copyOf(errors);
    }

    public static InvalidInputException of(ErrorCode code, String field, String message) {
        return new InvalidInputException(List.of(new InputError(code, field, message)));
    }

    public List<InputError> errors() {
        return errors;
    }

    private static String firstMessage(List<InputError> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("Au moins une erreur est requise");
        }
        return errors.get(0).message();
    }
}
