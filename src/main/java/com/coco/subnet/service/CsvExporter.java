package com.coco.subnet.service;

import com.coco.subnet.model.AllocatedSubnet;
import com.coco.subnet.model.Cidr;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.SubnetInfo;
import com.coco.subnet.model.VlsmPlan;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/** Plan d'adressage au format CSV pour Excel : « ; », UTF-8 avec BOM, fins de ligne Windows. */
@Service
public class CsvExporter {

    static final String HEADER =
            "VLAN;Nom;Hôtes demandés;Réseau;Masque;Passerelle;Première;Dernière;Broadcast;Hôtes utilisables;Libres";

    public String toCsv(VlsmPlan plan) {
        StringBuilder csv = new StringBuilder("\uFEFF").append(HEADER).append("\r\n");
        for (AllocatedSubnet allocated : plan.subnets()) {
            Requirement r = allocated.requirement();
            SubnetInfo s = allocated.subnet();
            csv.append(r.vlanId() == null ? "" : r.vlanId()).append(';')
                    .append(r.name()).append(';')
                    .append(r.hosts()).append(';')
                    .append(s.cidr()).append(';')
                    .append(s.subnetMask()).append(';')
                    .append(allocated.gateway() == null ? "" : allocated.gateway()).append(';')
                    .append(s.firstUsable()).append(';')
                    .append(s.lastUsable()).append(';')
                    .append(s.broadcastAddress()).append(';')
                    .append(s.usableHosts()).append(';')
                    .append(allocated.freeHosts()).append("\r\n");
        }
        return csv.toString();
    }

    public byte[] toBytes(VlsmPlan plan) {
        return toCsv(plan).getBytes(StandardCharsets.UTF_8);
    }

    public String fileName(VlsmPlan plan) {
        Cidr network = plan.network();
        return "plan-adressage-" + network.address() + "-" + network.prefix() + ".csv";
    }
}
