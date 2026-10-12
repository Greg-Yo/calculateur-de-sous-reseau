package com.coco.subnet.controller;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import jakarta.validation.ConstraintViolation;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Traduit les erreurs @Valid en InputError, en retrouvant le code depuis le gabarit {error.CODE}. */
final class ValidationErrors {

    private static final Pattern TEMPLATE = Pattern.compile("\\{error\\.([A-Z_]+)}");

    private ValidationErrors() {
    }

    static List<InputError> from(BindingResult result) {
        return result.getFieldErrors().stream()
                .map(error -> new InputError(codeOf(error), error.getField(), error.getDefaultMessage()))
                .toList();
    }

    static ErrorCode codeOf(FieldError error) {
        if (error.contains(ConstraintViolation.class)) {
            Matcher matcher = TEMPLATE.matcher(error.unwrap(ConstraintViolation.class).getMessageTemplate());
            if (matcher.matches()) {
                return ErrorCode.valueOf(matcher.group(1));
            }
        }
        return ErrorCode.MISSING_FIELD;
    }
}
