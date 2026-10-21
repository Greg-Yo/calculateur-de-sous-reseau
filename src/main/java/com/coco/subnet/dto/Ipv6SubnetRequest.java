package com.coco.subnet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Le nom n'a pas d'annotation : c'est le modèle qui le valide (codes EMPTY_NAME / INVALID_NAME). */
public record Ipv6SubnetRequest(@Schema(example = "WAN1") String name) {
}
