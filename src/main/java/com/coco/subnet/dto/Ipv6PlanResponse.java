package com.coco.subnet.dto;

import com.coco.subnet.model.Ipv6Plan;

import java.util.List;

/** Plan IPv6. availableSubnets et remainingSubnets sont en texte : ils peuvent dépasser 2^53. */
public record Ipv6PlanResponse(String network,
                               int subnetPrefix,
                               String mode,
                               List<Ipv6SubnetEntry> subnets,
                               int usedSubnets,
                               String availableSubnets,
                               String remainingSubnets,
                               List<String> warnings) {

    public static Ipv6PlanResponse from(Ipv6Plan plan) {
        return new Ipv6PlanResponse(plan.network().toString(), plan.subnetPrefix(), plan.mode().name(),
                plan.subnets().stream().map(Ipv6SubnetEntry::from).toList(), plan.usedSubnets(),
                plan.availableSubnets().toString(), plan.remainingSubnets().toString(), plan.warnings());
    }
}
