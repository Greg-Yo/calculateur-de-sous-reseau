package com.coco.subnet.model;

import java.math.BigInteger;
import java.util.List;

/** Plan IPv6 complet : sous-réseaux attribués, nombre de sous-réseaux disponibles dans le parent, avertissements. */
public record Ipv6Plan(Ipv6Cidr network,
                       int subnetPrefix,
                       PlanMode mode,
                       List<Ipv6AllocatedSubnet> subnets,
                       BigInteger availableSubnets,
                       List<String> warnings) {

    public int usedSubnets() {
        return subnets.size();
    }

    public BigInteger remainingSubnets() {
        return availableSubnets.subtract(BigInteger.valueOf(subnets.size()));
    }
}
