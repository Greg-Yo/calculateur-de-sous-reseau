package com.coco.subnet.model;

import java.util.List;

/** Le plan d'adressage complet, dans l'ordre d'allocation. */
public record VlsmPlan(Cidr network,
                       PlanMode mode,
                       List<AllocatedSubnet> subnets,
                       long usedAddresses,
                       long remainingAddresses,
                       List<String> warnings) {
}
