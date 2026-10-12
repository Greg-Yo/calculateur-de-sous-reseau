package com.coco.subnet.dto;

import com.coco.subnet.model.CidrResult;
import com.coco.subnet.model.SubnetInfo;

import java.util.List;

/** Résultat CIDR tel qu'il est affiché (JSON ou page web). */
public record SubnetResponse(String cidr,
                             String networkAddress,
                             String subnetMask,
                             String wildcardMask,
                             String broadcastAddress,
                             String firstUsable,
                             String lastUsable,
                             long usableHosts,
                             long totalAddresses,
                             List<String> warnings) {

    public static SubnetResponse from(CidrResult result) {
        SubnetInfo s = result.subnet();
        return new SubnetResponse(s.cidr().toString(), s.networkAddress().toString(), s.subnetMask().toString(),
                s.wildcardMask().toString(), s.broadcastAddress().toString(), s.firstUsable().toString(),
                s.lastUsable().toString(), s.usableHosts(), s.totalAddresses(), result.warnings());
    }
}
