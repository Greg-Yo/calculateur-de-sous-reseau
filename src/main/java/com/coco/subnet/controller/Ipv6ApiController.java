package com.coco.subnet.controller;

import com.coco.subnet.dto.Ipv6PlanRequest;
import com.coco.subnet.dto.Ipv6PlanResponse;
import com.coco.subnet.dto.Ipv6SubnetResponse;
import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.model.Ipv6Plan;
import com.coco.subnet.model.Ipv6Requirement;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.service.Ipv6CsvExporter;
import com.coco.subnet.service.Ipv6PlanService;
import com.coco.subnet.service.Ipv6SubnetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/ipv6")
@Tag(name = "Calculateur IPv6", description = "Analyse d'un préfixe IPv6 et plan d'adressage IPv6")
public class Ipv6ApiController {

    private final Ipv6SubnetService subnetService;
    private final Ipv6PlanService planService;
    private final Ipv6CsvExporter csvExporter;

    public Ipv6ApiController(Ipv6SubnetService subnetService, Ipv6PlanService planService, Ipv6CsvExporter csvExporter) {
        this.subnetService = subnetService;
        this.planService = planService;
        this.csvExporter = csvExporter;
    }

    @GetMapping(value = "/subnets", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Analyse un préfixe IPv6",
            description = "Adresse réseau (abrégée et complète), dernière adresse, nombre d'adresses, nombre de /64 "
                    + "et type d'adresse. Les grands nombres sont renvoyés en texte.")
    public Ipv6SubnetResponse subnet(@Parameter(example = "2001:db8:acad:10::/64") @RequestParam String cidr) {
        return Ipv6SubnetResponse.from(subnetService.calculate(cidr));
    }

    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Découpe un préfixe IPv6 en sous-réseaux de même taille",
            description = "Envoyer une seule forme : vlanCount (VLAN10, VLAN20…), vlans ou subnets (sans VLAN). "
                    + "subnetPrefix vaut 64 par défaut. Les grands nombres sont renvoyés en texte.")
    public Ipv6PlanResponse plan(@Valid @RequestBody Ipv6PlanRequest request) {
        return Ipv6PlanResponse.from(allocate(request));
    }

    @PostMapping(value = "/plan/csv", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "text/csv")
    @Operation(summary = "Plan d'adressage IPv6 au format CSV",
            description = "Même requête que /api/ipv6/plan. Les erreurs sont renvoyées en application/problem+json.")
    public ResponseEntity<byte[]> planCsv(@Valid @RequestBody Ipv6PlanRequest request) {
        Ipv6Plan plan = allocate(request);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(csvExporter.fileName(plan)).build().toString())
                .body(csvExporter.toBytes(plan));
    }

    /** Choisit la forme de la requête, applique /64 par défaut, puis délègue au service. */
    private Ipv6Plan allocate(Ipv6PlanRequest request) {
        boolean hasCount = request.vlanCount() != null;
        boolean hasVlans = request.vlans() != null;
        boolean hasSubnets = request.subnets() != null;
        int forms = (hasCount ? 1 : 0) + (hasVlans ? 1 : 0) + (hasSubnets ? 1 : 0);
        if (forms > 1) {
            throw InvalidInputException.of(ErrorCode.AMBIGUOUS_REQUEST, null,
                    "Envoyez une seule forme : vlanCount, vlans ou subnets");
        }
        if (forms == 0) {
            throw InvalidInputException.of(ErrorCode.NO_REQUIREMENTS, "vlans", "Ajoutez au moins un VLAN ou un besoin");
        }
        Integer subnetPrefix = request.subnetPrefix() == null
                ? Ipv6PlanService.DEFAULT_SUBNET_PREFIX : request.subnetPrefix();
        if (hasSubnets) {
            List<Ipv6Requirement> subnets = request.subnets().stream()
                    .map(s -> s == null ? null : new Ipv6Requirement(null, s.name()))
                    .toList();
            return planService.allocate(request.network(), subnetPrefix, PlanMode.REQUIREMENTS, subnets);
        }
        List<Ipv6Requirement> vlans = hasVlans
                ? request.vlans().stream().map(v -> v == null ? null : new Ipv6Requirement(v.id(), v.name())).toList()
                : planService.generateVlans(request.vlanCount());
        return planService.allocate(request.network(), subnetPrefix, PlanMode.VLAN, vlans);
    }
}
