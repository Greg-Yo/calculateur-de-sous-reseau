package com.coco.subnet.model;

import java.math.BigInteger;

/** Toutes les caractéristiques d'un préfixe IPv6. subnets64 est null si le préfixe est plus long que /64. */
public record Ipv6SubnetInfo(Ipv6Cidr cidr,
                             Ipv6Address networkAddress,
                             Ipv6Address lastAddress,
                             BigInteger totalAddresses,
                             BigInteger subnets64,
                             Ipv6AddressType type) {

    public static Ipv6SubnetInfo of(Ipv6Cidr input) {
        Ipv6Cidr cidr = input.toNetwork();
        BigInteger subnets64 = cidr.prefix() <= 64 ? BigInteger.ONE.shiftLeft(64 - cidr.prefix()) : null;
        return new Ipv6SubnetInfo(cidr, cidr.address(), cidr.lastAddress(), cidr.size(), subnets64,
                Ipv6AddressType.of(cidr));
    }
}
