package com.coco.subnet.model;

/** Toutes les caractéristiques d'un sous-réseau. */
public record SubnetInfo(Cidr cidr,
                         Ipv4Address networkAddress,
                         Ipv4Address subnetMask,
                         Ipv4Address wildcardMask,
                         Ipv4Address broadcastAddress,
                         Ipv4Address firstUsable,
                         Ipv4Address lastUsable,
                         long usableHosts,
                         long totalAddresses) {

    public static SubnetInfo of(Cidr input) {
        Cidr cidr = input.toNetwork();
        Ipv4Address network = cidr.address();
        Ipv4Address mask = cidr.mask();
        Ipv4Address wildcard = mask.not();
        Ipv4Address broadcast = network.or(wildcard);
        long total = cidr.size();
        return switch (cidr.prefix()) {
            // /32 : une seule adresse, qui est l'hôte lui-même
            case 32 -> new SubnetInfo(cidr, network, mask, wildcard, broadcast, network, network, 1, total);
            // /31 : lien point à point (RFC 3021), les 2 adresses sont utilisables
            case 31 -> new SubnetInfo(cidr, network, mask, wildcard, broadcast, network, broadcast, 2, total);
            default -> new SubnetInfo(cidr, network, mask, wildcard, broadcast,
                    network.plus(1), broadcast.plus(-1), total - 2, total);
        };
    }
}
