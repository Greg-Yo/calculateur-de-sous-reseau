package com.coco.subnet.model;

/** Un sous-réseau demandé : un ID de VLAN (mode VLAN) et un nom. Null tant que non validé. */
public record Ipv6Requirement(Integer vlanId, String name) {
}
