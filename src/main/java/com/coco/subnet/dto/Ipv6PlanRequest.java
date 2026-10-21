package com.coco.subnet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Exactement une forme : vlanCount (VLAN10, VLAN20…), vlans ou subnets (sans VLAN).
 * network, subnetPrefix et vlanCount n'ont pas d'annotation : c'est le modèle qui les valide
 * (le message de SUBNET_PREFIX_OUT_OF_RANGE dépend du préfixe parent).
 */
public record Ipv6PlanRequest(
        @Schema(example = "2001:db8:acad::/48") String network,
        @Schema(example = "64", description = "Facultatif : 64 par défaut") Integer subnetPrefix,
        @Schema(example = "4") Integer vlanCount,
        @Size(max = 100, message = "{error.TOO_MANY_REQUIREMENTS}")
        List<@NotNull(message = "{error.MISSING_FIELD}") @Valid Ipv6VlanRequest> vlans,
        @Size(max = 100, message = "{error.TOO_MANY_REQUIREMENTS}")
        List<@NotNull(message = "{error.MISSING_FIELD}") @Valid Ipv6SubnetRequest> subnets) {
}
