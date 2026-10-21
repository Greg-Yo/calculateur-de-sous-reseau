package com.coco.subnet.service;

import com.coco.subnet.model.Ipv6AllocatedSubnet;
import com.coco.subnet.model.Ipv6Cidr;
import com.coco.subnet.model.Ipv6Plan;
import com.coco.subnet.model.Ipv6Requirement;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/** Plan IPv6 au format CSV pour Excel : « ; », UTF-8 avec BOM, fins de ligne Windows, adresses compressées. */
@Service
public class Ipv6CsvExporter {

    static final String HEADER = "VLAN;Nom;Réseau;Passerelle;Première adresse;Dernière adresse";

    public String toCsv(Ipv6Plan plan) {
        StringBuilder csv = new StringBuilder("\uFEFF").append(HEADER).append("\r\n");
        for (Ipv6AllocatedSubnet allocated : plan.subnets()) {
            Ipv6Requirement r = allocated.requirement();
            csv.append(r.vlanId() == null ? "" : r.vlanId()).append(';')
                    .append(r.name()).append(';')
                    .append(allocated.cidr()).append(';')
                    .append(allocated.gateway() == null ? "" : allocated.gateway()).append(';')
                    .append(allocated.firstAddress()).append(';')
                    .append(allocated.lastAddress()).append("\r\n");
        }
        return csv.toString();
    }

    public byte[] toBytes(Ipv6Plan plan) {
        return toCsv(plan).getBytes(StandardCharsets.UTF_8);
    }

    /** Le « : » est interdit dans les noms de fichiers sous Windows : il est remplacé par « - ». */
    public String fileName(Ipv6Plan plan) {
        Ipv6Cidr network = plan.network();
        return "plan-ipv6-" + network.address().toString().replace(':', '-') + "-" + network.prefix() + ".csv";
    }
}
