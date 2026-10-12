package com.coco.subnet.model;

/** Un besoin et le sous-réseau qui lui est attribué. gateway vaut null hors mode VLAN. */
public record AllocatedSubnet(Requirement requirement, SubnetInfo subnet, Ipv4Address gateway, long freeHosts) {
}
