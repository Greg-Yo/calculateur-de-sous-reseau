package com.coco.subnet.service;

import com.coco.subnet.model.Ipv6Plan;
import com.coco.subnet.model.Ipv6Requirement;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.coco.subnet.model.PlanMode.REQUIREMENTS;
import static com.coco.subnet.model.PlanMode.VLAN;
import static org.assertj.core.api.Assertions.assertThat;

class Ipv6CsvExporterTest {

    private final Ipv6PlanService planService = new Ipv6PlanService();
    private final Ipv6CsvExporter exporter = new Ipv6CsvExporter();

    private Ipv6Plan vlanPlan() {
        return planService.allocate("2001:db8:acad::/48", 64, VLAN, planService.generateVlans(4));
    }

    private Ipv6Plan pointToPointPlan() {
        return planService.allocate("2001:db8:acad:ff::/64", 127, REQUIREMENTS,
                List.of(new Ipv6Requirement(null, "WAN1"), new Ipv6Requirement(null, "WAN2")));
    }

    @Test
    void startsWithTheExactHeader() {
        assertThat(exporter.toCsv(vlanPlan()))
                .startsWith("\uFEFFVLAN;Nom;Réseau;Passerelle;Première adresse;Dernière adresse\r\n");
    }

    @Test
    void vlanRowOfTheReferenceExample() {
        assertThat(exporter.toCsv(vlanPlan()).split("\r\n")[1])
                .isEqualTo("10;VLAN10;2001:db8:acad::/64;2001:db8:acad::1;2001:db8:acad::;2001:db8:acad:0:ffff:ffff:ffff:ffff");
    }

    @Test
    void planWithoutVlansLeavesVlanAndGatewayEmpty() {
        assertThat(exporter.toCsv(pointToPointPlan())).isEqualTo(
                "\uFEFFVLAN;Nom;Réseau;Passerelle;Première adresse;Dernière adresse\r\n"
                        + ";WAN1;2001:db8:acad:ff::/127;;2001:db8:acad:ff::;2001:db8:acad:ff::1\r\n"
                        + ";WAN2;2001:db8:acad:ff::2/127;;2001:db8:acad:ff::2;2001:db8:acad:ff::3\r\n");
    }

    @Test
    void bytesStartWithTheUtf8BomAndLinesEndWithCrLf() {
        byte[] bytes = exporter.toBytes(vlanPlan());

        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertThat(text).endsWith("\r\n");
        assertThat(text.split("\r\n")).hasSize(5).allSatisfy(line -> assertThat(line).doesNotContain("\n"));
    }

    @Test
    void accentedNamesAreEncodedInUtf8() {
        Ipv6Plan plan = planService.allocate("2001:db8:acad::/48", 64, VLAN, List.of(new Ipv6Requirement(10, "Comptabilité")));

        String text = new String(exporter.toBytes(plan), StandardCharsets.UTF_8);

        assertThat(text).contains("\r\n10;Comptabilité;2001:db8:acad::/64;");
    }

    @Test
    void fileNameReplacesColonsWithDashes() {
        assertThat(exporter.fileName(vlanPlan())).isEqualTo("plan-ipv6-2001-db8-acad---48.csv");
    }
}
