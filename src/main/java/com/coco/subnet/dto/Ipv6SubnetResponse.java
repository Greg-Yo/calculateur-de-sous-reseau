package com.coco.subnet.dto;

import com.coco.subnet.model.Ipv6CidrResult;
import com.coco.subnet.model.Ipv6SubnetInfo;

import java.util.List;

/**
 * Analyse d'un préfixe IPv6 telle qu'elle est affichée (JSON ou page web).
 * Les grands nombres sont en texte : au-delà de 2^53, un nombre JSON perd sa précision en JavaScript.
 * subnets64 est null si le préfixe est plus long que /64.
 */
public record Ipv6SubnetResponse(String cidr,
                                 String networkAddress,
                                 String networkAddressFull,
                                 String lastAddress,
                                 int prefixLength,
                                 String totalAddresses,
                                 String totalAddressesPower,
                                 String subnets64,
                                 String addressType,
                                 String addressTypeLabel,
                                 List<String> warnings) {

    public static Ipv6SubnetResponse from(Ipv6CidrResult result) {
        Ipv6SubnetInfo s = result.subnet();
        int prefix = s.cidr().prefix();
        return new Ipv6SubnetResponse(s.cidr().toString(), s.networkAddress().toString(),
                s.networkAddress().toFullString(), s.lastAddress().toString(), prefix,
                s.totalAddresses().toString(), "2^" + (128 - prefix),
                s.subnets64() == null ? null : s.subnets64().toString(),
                s.type().name(), s.type().label(), result.warnings());
    }
}
