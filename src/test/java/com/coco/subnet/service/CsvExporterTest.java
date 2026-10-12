package com.coco.subnet.service;

import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.VlsmPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.coco.subnet.model.PlanMode.REQUIREMENTS;
import static com.coco.subnet.model.PlanMode.VLAN;
import static org.assertj.core.api.Assertions.assertThat;

class CsvExporterTest {

    private final VlsmService vlsm = new VlsmService();
    private final CsvExporter exporter = new CsvExporter();

    @Test
    void vlanPlan() {
        VlsmPlan plan = vlsm.allocate("192.168.10.0/24", VLAN, List.of(new Requirement(10, "Comptabilité", 30L)));

        assertThat(exporter.toCsv(plan)).isEqualTo(
                "\uFEFFVLAN;Nom;Hôtes demandés;Réseau;Masque;Passerelle;Première;Dernière;Broadcast;Hôtes utilisables;Libres\r\n"
                        + "10;Comptabilité;30;192.168.10.0/26;255.255.255.192;192.168.10.1;192.168.10.1;192.168.10.62;192.168.10.63;62;31\r\n");
    }

    @Test
    void planWithoutVlansLeavesVlanAndGatewayEmpty() {
        VlsmPlan plan = vlsm.allocate("192.168.10.0/24", REQUIREMENTS, List.of(new Requirement(null, "WAN", 2L)));

        assertThat(exporter.toCsv(plan)).endsWith(
                "\r\n;WAN;2;192.168.10.0/30;255.255.255.252;;192.168.10.1;192.168.10.2;192.168.10.3;2;0\r\n");
    }

    @Test
    void bytesStartWithTheUtf8Bom() {
        VlsmPlan plan = vlsm.allocate("192.168.10.0/24", REQUIREMENTS, List.of(new Requirement(null, "WAN", 2L)));

        byte[] bytes = exporter.toBytes(plan);

        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    void fileNameUsesTheCorrectedNetwork() {
        VlsmPlan plan = vlsm.allocate("192.168.10.37/24", REQUIREMENTS, List.of(new Requirement(null, "WAN", 2L)));

        assertThat(exporter.fileName(plan)).isEqualTo("plan-adressage-192.168.10.0-24.csv");
    }
}
