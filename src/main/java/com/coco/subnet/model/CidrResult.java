package com.coco.subnet.model;

import java.util.List;

/** Résultat d'un calcul CIDR, avec les avertissements éventuels (adresse corrigée). */
public record CidrResult(SubnetInfo subnet, List<String> warnings) {
}
