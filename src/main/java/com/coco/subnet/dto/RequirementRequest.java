package com.coco.subnet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Le nom n'a pas d'annotation : c'est le modèle qui le valide (codes EMPTY_NAME / INVALID_NAME). */
public record RequirementRequest(
        @Schema(example = "LAN-A") String name,
        @NotNull(message = "{error.MISSING_FIELD}")
        @Positive(message = "{error.HOSTS_NOT_POSITIVE}")
        @Schema(example = "50") Long hosts) {
}
