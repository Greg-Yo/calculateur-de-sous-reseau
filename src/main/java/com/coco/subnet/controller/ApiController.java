package com.coco.subnet.controller;

import com.coco.subnet.dto.SubnetResponse;
import com.coco.subnet.dto.VlsmRequest;
import com.coco.subnet.dto.VlsmResponse;
import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.VlsmPlan;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @PostMapping(value = "/vlsm", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Découpe un réseau (VLSM ou VLAN)",
            description = "Envoyer une seule forme : requirements, vlans, ou vlanCount + hostsPerVlan.")
    public VlsmResponse vlsm(@Valid @RequestBody VlsmRequest request) {
        return VlsmResponse.from(plan(request));
    }

    /** Choisit la forme de la requête, puis délègue au service. */
    private VlsmPlan plan(VlsmRequest request) {
        boolean hasRequirements = request.requirements() != null;
        boolean hasVlans = request.vlans() != null;
        boolean hasShortcut = request.vlanCount() != null || request.hostsPerVlan() != null;
        int forms = (hasRequirements ? 1 : 0) + (hasVlans ? 1 : 0) + (hasShortcut ? 1 : 0);
        if (forms > 1) {
            throw InvalidInputException.of(ErrorCode.AMBIGUOUS_REQUEST, null,
                    "Envoyez une seule forme : requirements, vlans ou vlanCount + hostsPerVlan");
        }
        if (forms == 0) {
            throw InvalidInputException.of(ErrorCode.NO_REQUIREMENTS, "vlans", "Ajoutez au moins un VLAN ou un besoin");
        }
        if (hasRequirements) {
            List<Requirement> requirements = request.requirements().stream()
                    .map(r -> r == null ? null : new Requirement(null, r.name(), r.hosts()))
                    .toList();
            return vlsmService.allocate(request.network(), PlanMode.REQUIREMENTS, requirements);
        }
        if (hasVlans) {
            List<Requirement> vlans = request.vlans().stream()
                    .map(v -> v == null ? null : new Requirement(v.id(), v.name(), v.hosts()))
                    .toList();
            return vlsmService.allocate(request.network(), PlanMode.VLAN, vlans);
        }
        return vlsmService.allocate(request.network(), PlanMode.VLAN,
                vlsmService.generateUniformVlans(request.vlanCount(), request.hostsPerVlan()));
    }
}
