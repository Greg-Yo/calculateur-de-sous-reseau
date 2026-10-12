package com.coco.subnet.service;

import com.coco.subnet.model.AllocatedSubnet;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.VlsmPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.coco.subnet.model.PlanMode.REQUIREMENTS;
import static com.coco.subnet.model.PlanMode.VLAN;
import static org.assertj.core.api.Assertions.assertThat;

class VlsmServiceAllocationTest {

    private final VlsmService service = new VlsmService();

    private static Requirement need(String name, long hosts) {
        return new Requirement(null, name, hosts);
    }

    private static Requirement vlan(int id, long hosts) {
        return new Requirement(id, "VLAN" + id, hosts);
    }

    private static List<String> cidrs(VlsmPlan plan) {
        return plan.subnets().stream().map(s -> s.subnet().cidr().toString()).toList();
    }

    @ParameterizedTest(name = "{0} {1} hôtes -> bloc {2} (/{3})")
    @CsvSource({
            "REQUIREMENTS,1,4,30", "REQUIREMENTS,2,4,30", "REQUIREMENTS,3,8,29", "REQUIREMENTS,50,64,26",
            "REQUIREMENTS,62,64,26", "REQUIREMENTS,63,128,25", "REQUIREMENTS,254,256,24",
            "REQUIREMENTS,4294967294,4294967296,0",
            "VLAN,1,4,30", "VLAN,2,8,29", "VLAN,29,32,27", "VLAN,30,64,26", "VLAN,4294967293,4294967296,0"
    })
    void blockSizeIsTheSmallestFittingPowerOfTwo(PlanMode mode, long hosts, long block, int prefix) {
        assertThat(VlsmService.blockSize(mode, hosts)).isEqualTo(block);
        assertThat(VlsmService.prefixOf(block)).isEqualTo(prefix);
    }

    @Test
    void referenceExampleWithoutVlans() {
        VlsmPlan plan = service.allocate("192.168.10.0/24", REQUIREMENTS,
                List.of(need("LAN-A", 50), need("LAN-B", 20), need("LAN-C", 10), need("WAN", 2)));

        assertThat(cidrs(plan)).containsExactly(
                "192.168.10.0/26", "192.168.10.64/27", "192.168.10.96/28", "192.168.10.112/30");
        AllocatedSubnet lanA = plan.subnets().get(0);
        assertThat(lanA.subnet().firstUsable()).hasToString("192.168.10.1");
        assertThat(lanA.subnet().lastUsable()).hasToString("192.168.10.62");
        assertThat(lanA.subnet().broadcastAddress()).hasToString("192.168.10.63");
        assertThat(lanA.gateway()).isNull();
        assertThat(plan.subnets()).extracting(AllocatedSubnet::freeHosts).containsExactly(12L, 10L, 4L, 0L);
        assertThat(plan.usedAddresses()).isEqualTo(116);
        assertThat(plan.remainingAddresses()).isEqualTo(140);
        assertThat(plan.mode()).isEqualTo(REQUIREMENTS);
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void referenceExampleWithFourVlansOfThirtyHosts() {
        VlsmPlan plan = service.allocate("192.168.10.0/24", VLAN,
                List.of(vlan(10, 30), vlan(20, 30), vlan(30, 30), vlan(40, 30)));

        assertThat(cidrs(plan)).containsExactly(
                "192.168.10.0/26", "192.168.10.64/26", "192.168.10.128/26", "192.168.10.192/26");
        assertThat(plan.subnets()).extracting(s -> s.gateway().toString())
                .containsExactly("192.168.10.1", "192.168.10.65", "192.168.10.129", "192.168.10.193");
        assertThat(plan.subnets()).extracting(AllocatedSubnet::freeHosts).containsOnly(31L);
        assertThat(plan.usedAddresses()).isEqualTo(256);
        assertThat(plan.remainingAddresses()).isZero();
    }

    @Test
    void gatewayEdgeCase() {
        VlsmPlan plan = service.allocate("192.168.10.0/24", VLAN, List.of(vlan(10, 30), vlan(20, 29)));

        assertThat(cidrs(plan)).containsExactly("192.168.10.0/26", "192.168.10.64/27");
        assertThat(plan.subnets().get(1).freeHosts()).isZero();
    }

    @Test
    void equalBlocksKeepTheInputOrder() {
        VlsmPlan plan = service.allocate("10.0.0.0/24", REQUIREMENTS,
                List.of(need("A", 13), need("B", 50), need("C", 14)));

        assertThat(plan.subnets()).extracting(s -> s.requirement().name()).containsExactly("B", "A", "C");
        assertThat(cidrs(plan)).containsExactly("10.0.0.0/26", "10.0.0.64/28", "10.0.0.80/28");
    }

    @Test
    void hostAddressAsParentIsCorrectedWithAWarning() {
        VlsmPlan plan = service.allocate("192.168.10.37/24", REQUIREMENTS, List.of(need("LAN", 10)));

        assertThat(plan.network()).hasToString("192.168.10.0/24");
        assertThat(cidrs(plan)).containsExactly("192.168.10.0/28");
        assertThat(plan.warnings()).containsExactly("Adresse corrigée vers le réseau 192.168.10.0/24");
    }

    @Test
    void highAddressesAreAllocatedCorrectly() {
        VlsmPlan plan = service.allocate("200.1.0.0/16", REQUIREMENTS, List.of(need("A", 1000), need("B", 500)));

        assertThat(cidrs(plan)).containsExactly("200.1.0.0/22", "200.1.4.0/23");
        assertThat(plan.subnets().get(0).subnet().broadcastAddress()).hasToString("200.1.3.255");
        assertThat(plan.subnets().get(1).subnet().broadcastAddress()).hasToString("200.1.5.255");
    }

    @Test
    void aRequirementCanFillTheParentExactly() {
        VlsmPlan plan = service.allocate("192.168.1.0/24", REQUIREMENTS, List.of(need("TOUT", 254)));

        assertThat(cidrs(plan)).containsExactly("192.168.1.0/24");
        assertThat(plan.remainingAddresses()).isZero();
    }

    @Test
    void theWholeIpv4SpaceFitsInOneBlock() {
        VlsmPlan plan = service.allocate("0.0.0.0/0", REQUIREMENTS, List.of(need("TOUT", 4_294_967_294L)));

        assertThat(cidrs(plan)).containsExactly("0.0.0.0/0");
        assertThat(plan.usedAddresses()).isEqualTo(4_294_967_296L);
        assertThat(plan.remainingAddresses()).isZero();
    }
}
