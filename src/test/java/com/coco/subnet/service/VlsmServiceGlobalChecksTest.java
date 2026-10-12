package com.coco.subnet.service;

import com.coco.subnet.model.InputError;
import com.coco.subnet.model.Requirement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.coco.subnet.TestErrors.errors;
import static com.coco.subnet.TestErrors.firstError;
import static com.coco.subnet.model.ErrorCode.DUPLICATE_NAME;
import static com.coco.subnet.model.ErrorCode.DUPLICATE_VLAN_ID;
import static com.coco.subnet.model.ErrorCode.NETWORK_TOO_SMALL;
import static com.coco.subnet.model.ErrorCode.REQUIREMENT_TOO_LARGE;
import static com.coco.subnet.model.PlanMode.REQUIREMENTS;
import static com.coco.subnet.model.PlanMode.VLAN;
import static org.assertj.core.api.Assertions.assertThat;

class VlsmServiceGlobalChecksTest {

    private final VlsmService service = new VlsmService();

    private static final String NET = "192.168.10.0/24";

    @Test
    void duplicateVlanIdIsReportedOnceOnTheSecondRow() {
        List<InputError> errors = errors(() -> service.allocate(NET, VLAN,
                List.of(new Requirement(10, null, 5L), new Requirement(20, null, 5L), new Requirement(20, null, 5L))));

        assertThat(errors).containsExactly(new InputError(DUPLICATE_VLAN_ID, "vlans[2].id", "VLAN 20 est en double"));
    }

    @Test
    void duplicateNamesIgnoreCase() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS,
                List.of(new Requirement(null, "RH", 5L), new Requirement(null, "rh", 5L)))))
                .isEqualTo(new InputError(DUPLICATE_NAME, "requirements[1].name", "Le nom 'rh' est utilisé deux fois"));
    }

    @Test
    void duplicateVlanNames() {
        assertThat(firstError(() -> service.allocate(NET, VLAN,
                List.of(new Requirement(10, "Compta", 5L), new Requirement(20, "compta", 5L)))))
                .isEqualTo(new InputError(DUPLICATE_NAME, "vlans[1].name", "Le nom 'compta' est utilisé deux fois"));
    }

    @Test
    void duplicatesAreCheckedBeforeCapacity() {
        List<InputError> errors = errors(() -> service.allocate(NET, VLAN,
                List.of(new Requirement(10, null, 300L), new Requirement(10, null, 5L))));

        assertThat(errors).extracting(InputError::code).containsExactly(DUPLICATE_VLAN_ID);
    }

    @Test
    void requirementLargerThanTheParent() {
        assertThat(firstError(() -> service.allocate(NET, VLAN, List.of(new Requirement(10, null, 300L)))))
                .isEqualTo(new InputError(REQUIREMENT_TOO_LARGE, "vlans[0].hosts",
                        "VLAN 10 (300 hôtes, bloc /23) ne tient pas dans un /24"));
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS, List.of(new Requirement(null, "LAN-A", 300L)))))
                .isEqualTo(new InputError(REQUIREMENT_TOO_LARGE, "requirements[0].hosts",
                        "LAN-A (300 hôtes, bloc /23) ne tient pas dans un /24"));
    }

    @Test
    void tooLargeRequirementsAreAllReportedWithoutTheTotal() {
        List<InputError> errors = errors(() -> service.allocate(NET, REQUIREMENTS,
                List.of(new Requirement(null, "A", 300L), new Requirement(null, "B", 10L), new Requirement(null, "C", 600L))));

        assertThat(errors).extracting(InputError::field).containsExactly("requirements[0].hosts", "requirements[2].hosts");
    }

    @Test
    void networkTooSmall() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS,
                List.of(new Requirement(null, "A", 200L), new Requirement(null, "B", 50L)))))
                .isEqualTo(new InputError(NETWORK_TOO_SMALL, "network",
                        "Le /24 (256 adresses) ne suffit pas : 320 nécessaires, il manque 64"));
    }

    @Test
    void overflowByASingleSlash30() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS,
                List.of(new Requirement(null, "A", 254L), new Requirement(null, "B", 1L)))).message())
                .isEqualTo("Le /24 (256 adresses) ne suffit pas : 260 nécessaires, il manque 4");
    }
}
