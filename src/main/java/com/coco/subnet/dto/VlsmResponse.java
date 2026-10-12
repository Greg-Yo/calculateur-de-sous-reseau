package com.coco.subnet.dto;

import com.coco.subnet.model.VlsmPlan;

import java.util.List;

public record VlsmResponse(String network,
                           String mode,
                           List<SubnetEntry> subnets,
                           long usedAddresses,
                           long remainingAddresses,
                           List<String> warnings) {

    public static VlsmResponse from(VlsmPlan plan) {
        return new VlsmResponse(plan.network().toString(), plan.mode().name(),
                plan.subnets().stream().map(SubnetEntry::from).toList(),
                plan.usedAddresses(), plan.remainingAddresses(), plan.warnings());
    }
}
