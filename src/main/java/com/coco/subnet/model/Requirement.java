package com.coco.subnet.model;

/** Un besoin d'adresses. Les champs peuvent être null tant qu'ils n'ont pas été validés. */
public record Requirement(Integer vlanId, String name, Long hosts) {
}
