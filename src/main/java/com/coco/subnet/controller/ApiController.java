package com.coco.subnet.controller;

import com.coco.subnet.dto.SubnetResponse;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Calculateur", description = "Calcul CIDR, découpage VLSM et plan par VLAN")
public class ApiController {

    private final SubnetService subnetService;
    private final VlsmService vlsmService;
    private final CsvExporter csvExporter;

    public ApiController(SubnetService subnetService, VlsmService vlsmService, CsvExporter csvExporter) {
        this.subnetService = subnetService;
        this.vlsmService = vlsmService;
        this.csvExporter = csvExporter;
    }

    @GetMapping(value = "/subnets", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Calcule un réseau CIDR",
            description = "Adresse réseau, masque, wildcard, broadcast, plage utilisable et nombre d'hôtes.")
    public SubnetResponse subnet(@Parameter(example = "192.168.10.0/24") @RequestParam String cidr) {
        return SubnetResponse.from(subnetService.calculate(cidr));
    }
}
