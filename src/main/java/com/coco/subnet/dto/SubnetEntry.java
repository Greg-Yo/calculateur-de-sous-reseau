package com.coco.subnet.dto;

import com.coco.subnet.model.AllocatedSubnet;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.SubnetInfo;

/** Une ligne du plan d'adressage. vlanId et gateway valent null hors mode VLAN. */
public record SubnetEntry(Integer vlanId,
                          String name,
                          long requestedHosts,
                          String gateway,
                          String cidr,
                          String networkAddress,
                          String subnetMask,
                          String wildcardMask,
                          String broadcastAddress,
                          String firstUsable,
                          String lastUsable,
                          long usableHosts,
                          long totalAddresses,
                          long freeHosts) {

    public static SubnetEntry from(AllocatedSubnet allocated) {
        Requirement r = allocated.requirement();
        SubnetInfo s = allocated.subnet();
        return new SubnetEntry(r.vlanId(), r.name(), r.hosts(),
                allocated.gateway() == null ? null : allocated.gateway().toString(),
                s.cidr().toString(), s.networkAddress().toString(), s.subnetMask().toString(),
                s.wildcardMask().toString(), s.broadcastAddress().toString(), s.firstUsable().toString(),
                s.lastUsable().toString(), s.usableHosts(), s.totalAddresses(), allocated.freeHosts());
    }
}
