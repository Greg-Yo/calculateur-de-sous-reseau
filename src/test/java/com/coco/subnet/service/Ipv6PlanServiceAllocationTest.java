package com.coco.subnet.service;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import com.coco.subnet.model.Ipv6AllocatedSubnet;
import com.coco.subnet.model.Ipv6Plan;
import com.coco.subnet.model.Ipv6Requirement;
import com.coco.subnet.model.PlanMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class Ipv6PlanServiceAllocationTest {

    private final Ipv6PlanService service = new Ipv6PlanService();

    private static List<Ipv6Requirement> named(String... names) {
        return Arrays.stream(names).map(n -> new Ipv6Requirement(null, n)).toList();
    }

    private static List<Ipv6Requirement> vlans(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> new Ipv6Requirement(i * 10, null)).toList();
    }

    private static List<String> cidrs(Ipv6Plan plan) {
        return plan.subnets().stream().map(s -> s.cidr().toString()).toList();
    }

    @Test
    void referenceExampleFourVlansIn48() {
        Ipv6Plan plan = service.allocate("2001:db8:acad::/48", 64, PlanMode.VLAN, service.generateVlans(4));

        assertThat(plan.subnets()).extracting(s -> s.requirement().vlanId(), s -> s.requirement().name(),
                        s -> s.cidr().toString(), s -> s.gateway().toString(),
                        s -> s.firstAddress().toString(), s -> s.lastAddress().toString())
                .containsExactly(
                        tuple(10, "VLAN10", "2001:db8:acad::/64", "2001:db8:acad::1", "2001:db8:acad::", "2001:db8:acad:0:ffff:ffff:ffff:ffff"),
                        tuple(20, "VLAN20", "2001:db8:acad:1::/64", "2001:db8:acad:1::1", "2001:db8:acad:1::", "2001:db8:acad:1:ffff:ffff:ffff:ffff"),
                        tuple(30, "VLAN30", "2001:db8:acad:2::/64", "2001:db8:acad:2::1", "2001:db8:acad:2::", "2001:db8:acad:2:ffff:ffff:ffff:ffff"),
                        tuple(40, "VLAN40", "2001:db8:acad:3::/64", "2001:db8:acad:3::1", "2001:db8:acad:3::", "2001:db8:acad:3:ffff:ffff:ffff:ffff"));
        assertThat(plan.network()).hasToString("2001:db8:acad::/48");
        assertThat(plan.subnetPrefix()).isEqualTo(64);
        assertThat(plan.mode()).isEqualTo(PlanMode.VLAN);
        assertThat(plan.usedSubnets()).isEqualTo(4);
        assertThat(plan.availableSubnets()).isEqualTo(BigInteger.valueOf(65_536));
        assertThat(plan.remainingSubnets()).isEqualTo(BigInteger.valueOf(65_532));
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void referenceExamplePointToPointLinks() {
        Ipv6Plan plan = service.allocate("2001:db8:acad:ff::/64", 127, PlanMode.REQUIREMENTS, named("WAN1", "WAN2"));

        assertThat(plan.subnets()).extracting(s -> s.requirement().name(), s -> s.cidr().toString(),
                        s -> s.firstAddress().toString(), s -> s.lastAddress().toString(), Ipv6AllocatedSubnet::gateway)
                .containsExactly(
                        tuple("WAN1", "2001:db8:acad:ff::/127", "2001:db8:acad:ff::", "2001:db8:acad:ff::1", null),
                        tuple("WAN2", "2001:db8:acad:ff::2/127", "2001:db8:acad:ff::2", "2001:db8:acad:ff::3", null));
        assertThat(plan.warnings())
                .containsExactly("Sous-réseaux /127 : plus longs que /64, l'autoconfiguration SLAAC ne fonctionnera pas");
    }

    @Test
    void shiftCrossesFromLowToHigh() {
        Ipv6Plan plan = service.allocate("2001:db8:acad::/62", 66, PlanMode.REQUIREMENTS, named("A", "B", "C", "D", "E"));

        assertThat(cidrs(plan)).containsExactly("2001:db8:acad::/66", "2001:db8:acad:0:4000::/66",
                "2001:db8:acad:0:8000::/66", "2001:db8:acad:0:c000::/66", "2001:db8:acad:1::/66");
    }

    @Test
    void topOfTheAddressSpaceDoesNotOverflow() {
        Ipv6Plan plan = service.allocate("ffff:ffff:ffff:ffff::/64", 66, PlanMode.REQUIREMENTS, named("A", "B", "C", "D"));

        assertThat(plan.subnets().get(3).cidr()).hasToString("ffff:ffff:ffff:ffff:c000::/66");
        assertThat(plan.subnets().get(3).lastAddress()).hasToString("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
        assertThat(plan.remainingSubnets()).isZero();
    }

    @Test
    void exactCapacityLeavesNothing() {
        Ipv6Plan plan = service.allocate("2001:db8:acad::/62", 64, PlanMode.VLAN, vlans(4));

        assertThat(plan.remainingSubnets()).isZero();
    }

    @Test
    void wholeAddressSpaceAsParent() {
        Ipv6Plan plan = service.allocate("::/0", 64, PlanMode.VLAN, vlans(1));

        assertThat(plan.availableSubnets()).isEqualTo(BigInteger.TWO.pow(64));
        assertThat(cidrs(plan)).containsExactly("::/64");
    }

    @Test
    void correctsTheParentAndWarnsAboutShortSubnets() {
        Ipv6Plan plan = service.allocate("2001:db8:acad:1234::/40", 56, PlanMode.VLAN, vlans(1));

        assertThat(plan.network()).hasToString("2001:db8:ac00::/40");
        assertThat(plan.warnings()).containsExactly(
                "Adresse corrigée vers le réseau 2001:db8:ac00::/40",
                "Sous-réseaux /56 : plus courts que /64 ; un réseau local utilise normalement un /64");
    }

    @Test
    void keepsTheNamesTypedForVlans() {
        Ipv6Plan plan = service.allocate("2001:db8::/48", 64, PlanMode.VLAN,
                List.of(new Ipv6Requirement(30, " Compta "), new Ipv6Requirement(10, "")));

        assertThat(plan.subnets()).extracting(s -> s.requirement().name()).containsExactly("Compta", "VLAN10");
        assertThat(cidrs(plan)).containsExactly("2001:db8::/64", "2001:db8:0:1::/64");
    }

    @Test
    void generatesNumberedVlans() {
        assertThat(service.generateVlans(3)).containsExactly(new Ipv6Requirement(10, "VLAN10"),
                new Ipv6Requirement(20, "VLAN20"), new Ipv6Requirement(30, "VLAN30"));
        assertThat(service.generateVlans(100)).last().isEqualTo(new Ipv6Requirement(1000, "VLAN1000"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void rejectsAVlanCountOutOfRange(int count) {
        InputError error = firstError(() -> service.generateVlans(count));

        assertThat(error.code()).isEqualTo(ErrorCode.VLAN_COUNT_OUT_OF_RANGE);
        assertThat(error.field()).isEqualTo("vlanCount");
    }

    @Test
    void rejectsAMissingVlanCount() {
        assertThat(firstError(() -> service.generateVlans(null)).code()).isEqualTo(ErrorCode.MISSING_FIELD);
    }
}
