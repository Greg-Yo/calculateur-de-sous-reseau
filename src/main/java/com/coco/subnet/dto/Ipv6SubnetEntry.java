package com.coco.subnet.dto;

import com.coco.subnet.model.Ipv6AllocatedSubnet;
import com.coco.subnet.model.Ipv6Requirement;

/** Une ligne du plan IPv6. vlanId et gateway valent null hors mode VLAN. */
public record Ipv6SubnetEntry(Integer vlanId,
                              String name,
                              String cidr,
                              String networkAddress,
                              String gateway,
                              String firstAddress,
                              String lastAddress) {

    public static Ipv6SubnetEntry from(Ipv6AllocatedSubnet allocated) {
        Ipv6Requirement r = allocated.requirement();
        return new Ipv6SubnetEntry(r.vlanId(), r.name(), allocated.cidr().toString(),
                allocated.cidr().networkAddress().toString(),
                allocated.gateway() == null ? null : allocated.gateway().toString(),
                allocated.firstAddress().toString(), allocated.lastAddress().toString());
    }
}
