package com.coco.subnet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Exactement une des trois formes : requirements, vlans, ou vlanCount + hostsPerVlan.
 * Le réseau n'a pas d'annotation : le modèle renvoie EMPTY_INPUT, comme partout ailleurs.
 */
public record VlsmRequest(
        @Schema(example = "192.168.10.0/24") String network,
        @Size(max = 100, message = "{error.TOO_MANY_REQUIREMENTS}")
        List<@NotNull(message = "{error.MISSING_FIELD}") @Valid RequirementRequest> requirements,
        @Size(max = 100, message = "{error.TOO_MANY_REQUIREMENTS}")
        List<@NotNull(message = "{error.MISSING_FIELD}") @Valid VlanRequest> vlans,
        @Min(value = 1, message = "{error.VLAN_COUNT_OUT_OF_RANGE}")
        @Max(value = 100, message = "{error.VLAN_COUNT_OUT_OF_RANGE}")
        @Schema(example = "4") Integer vlanCount,
        @Positive(message = "{error.HOSTS_NOT_POSITIVE}")
        @Schema(example = "30") Long hostsPerVlan) {
}
