package com.coco.subnet.model;

/** Un besoin et le sous-réseau qui lui est attribué. gateway est null hors mode VLAN. */
public record Ipv6AllocatedSubnet(Ipv6Requirement requirement, Ipv6Cidr cidr, Ipv6Address gateway) {

    public Ipv6Address firstAddress() {
        return cidr.networkAddress();
    }

    public Ipv6Address lastAddress() {
        return cidr.lastAddress();
    }
}
