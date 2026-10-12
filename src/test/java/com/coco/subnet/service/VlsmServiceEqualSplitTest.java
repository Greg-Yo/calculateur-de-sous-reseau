package com.coco.subnet.service;

import com.coco.subnet.model.InputError;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.coco.subnet.TestErrors.errors;
import static com.coco.subnet.TestErrors.firstError;
import static com.coco.subnet.model.ErrorCode.EMPTY_INPUT;
import static com.coco.subnet.model.ErrorCode.MISSING_FIELD;
import static com.coco.subnet.model.ErrorCode.NETWORK_TOO_SMALL;
import static com.coco.subnet.model.ErrorCode.PARENT_NOT_SPLITTABLE;
import static com.coco.subnet.model.ErrorCode.VLAN_COUNT_OUT_OF_RANGE;
import static org.assertj.core.api.Assertions.assertThat;

class VlsmServiceEqualSplitTest {

    private final VlsmService service = new VlsmService();

    @Test
    void fourVlansInASlash24GetASlash26Each() {
        assertThat(service.generateEqualSplitVlans("192.168.10.0/24", 4)).containsExactly(
                new Requirement(10, "VLAN10", 61L), new Requirement(20, "VLAN20", 61L),
                new Requirement(30, "VLAN30", 61L), new Requirement(40, "VLAN40", 61L));
    }

    @Test
    void threeVlansRoundUpToFourBlocks() {
        List<Requirement> vlans = service.generateEqualSplitVlans("192.168.10.0/24", 3);

        assertThat(vlans).hasSize(3).allMatch(v -> v.hosts() == 61L);
    }

    @Test
    void oneVlanTakesTheWholeNetwork() {
        assertThat(service.generateEqualSplitVlans("10.0.0.0/8", 1))
                .containsExactly(new Requirement(10, "VLAN10", 16_777_213L));
    }

    @Test
    void slash30IsTheSmallestBlock() {
        assertThat(service.generateEqualSplitVlans("192.168.10.0/28", 4))
                .allMatch(v -> v.hosts() == 1L);
    }

    @Test
    void networkTooSmallForTheVlanCount() {
        assertThat(firstError(() -> service.generateEqualSplitVlans("192.168.10.0/28", 5)))
                .isEqualTo(new InputError(NETWORK_TOO_SMALL, "network",
                        "Un /28 ne peut pas être découpé en 5 VLAN : il faudrait des blocs /31 (un /30 au minimum par VLAN)"));
    }

    @Test
    void splitThenAllocateGivesConsecutiveEqualBlocks() {
        var plan = service.allocate("192.168.10.0/24", PlanMode.VLAN,
                service.generateEqualSplitVlans("192.168.10.0/24", 3));

        assertThat(plan.subnets()).extracting(s -> s.subnet().cidr().toString())
                .containsExactly("192.168.10.0/26", "192.168.10.64/26", "192.168.10.128/26");
        assertThat(plan.remainingAddresses()).isEqualTo(64);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void vlanCountOutOfRange(int count) {
        assertThat(firstError(() -> service.generateEqualSplitVlans("192.168.10.0/24", count)))
                .isEqualTo(new InputError(VLAN_COUNT_OUT_OF_RANGE, "vlanCount", "Le nombre de VLAN doit être entre 1 et 100"));
    }

    @Test
    void networkAndCountAreReportedTogether() {
        assertThat(errors(() -> service.generateEqualSplitVlans("", null)))
                .extracting(InputError::code).containsExactly(EMPTY_INPUT, MISSING_FIELD);
    }

    @Test
    void slash31CannotBeSplit() {
        assertThat(firstError(() -> service.generateEqualSplitVlans("192.168.10.0/31", 1)).code())
                .isEqualTo(PARENT_NOT_SPLITTABLE);
    }
}
