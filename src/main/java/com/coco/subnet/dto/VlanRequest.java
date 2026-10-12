package com.coco.subnet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VlanRequest(
        @NotNull(message = "{error.MISSING_FIELD}")
        @Min(value = 1, message = "{error.VLAN_ID_OUT_OF_RANGE}")
        @Max(value = 4094, message = "{error.VLAN_ID_OUT_OF_RANGE}")
        @Schema(example = "10") Integer id,
        @Schema(example = "Compta", description = "Facultatif : VLAN<id> par défaut") String name,
        @NotNull(message = "{error.MISSING_FIELD}")
        @Positive(message = "{error.HOSTS_NOT_POSITIVE}")
        @Schema(example = "30", description = "Hôtes hors passerelle") Long hosts) {
}
