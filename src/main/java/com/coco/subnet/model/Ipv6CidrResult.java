package com.coco.subnet.model;

import java.util.List;

/** Résultat de l'analyse d'un préfixe IPv6, avec les avertissements éventuels (adresse corrigée). */
public record Ipv6CidrResult(Ipv6SubnetInfo subnet, List<String> warnings) {
}
