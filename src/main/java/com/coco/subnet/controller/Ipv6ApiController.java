package com.coco.subnet.controller;

import com.coco.subnet.dto.Ipv6SubnetResponse;
import com.coco.subnet.service.Ipv6SubnetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ipv6")
@Tag(name = "Calculateur IPv6", description = "Analyse d'un préfixe IPv6 et plan d'adressage IPv6")
public class Ipv6ApiController {

    private final Ipv6SubnetService subnetService;

    public Ipv6ApiController(Ipv6SubnetService subnetService) {
        this.subnetService = subnetService;
    }

    @GetMapping(value = "/subnets", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Analyse un préfixe IPv6",
            description = "Adresse réseau (abrégée et complète), dernière adresse, nombre d'adresses, nombre de /64 "
                    + "et type d'adresse. Les grands nombres sont renvoyés en texte.")
    public Ipv6SubnetResponse subnet(@Parameter(example = "2001:db8:acad:10::/64") @RequestParam String cidr) {
        return Ipv6SubnetResponse.from(subnetService.calculate(cidr));
    }
}
