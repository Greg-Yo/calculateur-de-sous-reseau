package com.coco.subnet.service;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.VlsmPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.coco.subnet.TestErrors.errors;
import static com.coco.subnet.TestErrors.firstError;
import static com.coco.subnet.model.ErrorCode.*;
import static com.coco.subnet.model.PlanMode.REQUIREMENTS;
import static com.coco.subnet.model.PlanMode.VLAN;
import static org.assertj.core.api.Assertions.assertThat;

class VlsmServiceValidationTest {

    private final VlsmService service = new VlsmService();

    private static final String NET = "192.168.10.0/24";

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "''|EMPTY_INPUT", "300.1.1.1/24|OCTET_OUT_OF_RANGE", "10.0.0.0|MISSING_PREFIX",
            "10.0.0.0/31|PARENT_NOT_SPLITTABLE", "10.0.0.1/32|PARENT_NOT_SPLITTABLE"
    })
    void invalidParentNetwork(String network, ErrorCode expected) {
        InputError error = firstError(() -> service.allocate(network, REQUIREMENTS, List.of(new Requirement(null, "A", 2L))));

        assertThat(error.code()).isEqualTo(expected);
        assertThat(error.field()).isEqualTo("network");
    }

    @Test
    void parentSlash31Message() {
        assertThat(firstError(() -> service.allocate("10.0.0.0/31", REQUIREMENTS, List.of(new Requirement(null, "A", 2L)))).message())
                .isEqualTo("Un /31 ou /32 ne peut pas être découpé");
    }

    @Test
    void noRequirements() {
        InputError nullList = firstError(() -> service.allocate(NET, VLAN, null));
        InputError emptyList = firstError(() -> service.allocate(NET, REQUIREMENTS, List.of()));

        assertThat(nullList).isEqualTo(new InputError(NO_REQUIREMENTS, "vlans", "Ajoutez au moins un VLAN ou un besoin"));
        assertThat(emptyList.field()).isEqualTo("requirements");
    }

    @Test
    void tooManyRequirements() {
        List<Requirement> rows = Collections.nCopies(101, new Requirement(null, "A", 1L));

        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS, rows)))
                .isEqualTo(new InputError(TOO_MANY_REQUIREMENTS, "requirements", "Maximum 100 sous-réseaux"));
    }

    @Test
    void vlanIdIsRequired() {
        assertThat(firstError(() -> service.allocate(NET, VLAN, List.of(new Requirement(null, null, 10L)))))
                .isEqualTo(new InputError(MISSING_FIELD, "vlans[0].id", "Ligne 1 : l'ID du VLAN est obligatoire"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4095, 5000})
    void vlanIdOutOfRange(int id) {
        assertThat(firstError(() -> service.allocate(NET, VLAN, List.of(new Requirement(id, null, 10L)))))
                .isEqualTo(new InputError(VLAN_ID_OUT_OF_RANGE, "vlans[0].id", "VLAN " + id + " : l'ID doit être entre 1 et 4094"));
    }

    @Test
    void invalidVlanIdUsesTheLineNumberInOtherMessages() {
        List<InputError> errors = errors(() -> service.allocate(NET, VLAN,
                List.of(new Requirement(10, null, 5L), new Requirement(5000, null, 0L))));

        assertThat(errors).extracting(InputError::message)
                .containsExactly("VLAN 5000 : l'ID doit être entre 1 et 4094", "Ligne 2 : au moins 1 hôte requis");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -5})
    void hostsMustBePositive(long hosts) {
        assertThat(firstError(() -> service.allocate(NET, VLAN, List.of(new Requirement(10, null, hosts)))))
                .isEqualTo(new InputError(HOSTS_NOT_POSITIVE, "vlans[0].hosts", "VLAN 10 : au moins 1 hôte requis"));
    }

    @Test
    void hostsAreRequired() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS, List.of(new Requirement(null, "LAN-A", null)))))
                .isEqualTo(new InputError(MISSING_FIELD, "requirements[0].hosts", "LAN-A : le nombre d'hôtes est obligatoire"));
    }

    @Test
    void hostsLimitDependsOnTheMode() {
        assertThat(firstError(() -> service.allocate("0.0.0.0/0", VLAN, List.of(new Requirement(10, null, 4_294_967_294L)))))
                .isEqualTo(new InputError(HOSTS_TOO_LARGE, "vlans[0].hosts",
                        "VLAN 10 : un sous-réseau IPv4 ne peut pas dépasser 4 294 967 293 hôtes (hors passerelle)"));
        assertThat(firstError(() -> service.allocate("0.0.0.0/0", REQUIREMENTS, List.of(new Requirement(null, "LAN-A", 4_294_967_295L)))))
                .isEqualTo(new InputError(HOSTS_TOO_LARGE, "requirements[0].hosts",
                        "LAN-A : un sous-réseau IPv4 ne peut pas dépasser 4 294 967 294 hôtes"));
    }

    @Test
    void largestValidVlanFitsInTheWholeSpace() {
        VlsmPlan plan = service.allocate("0.0.0.0/0", VLAN, List.of(new Requirement(10, null, 4_294_967_293L)));

        assertThat(plan.subnets().get(0).subnet().cidr()).hasToString("0.0.0.0/0");
        assertThat(plan.subnets().get(0).freeHosts()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void nameIsRequiredWithoutVlans(String name) {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS,
                List.of(new Requirement(null, "A", 2L), new Requirement(null, "B", 2L), new Requirement(null, name, 2L)))))
                .isEqualTo(new InputError(EMPTY_NAME, "requirements[2].name", "Le besoin n°3 doit avoir un nom"));
    }

    @Test
    void nullNameIsRequiredWithoutVlans() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS, List.of(new Requirement(null, null, 2L)))).code())
                .isEqualTo(EMPTY_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a;b", "-A1", "=SUM", "Salle Info", "@cmd", "abcdefghijklmnopqrstuvwxyz0123456"})
    void invalidNames(String name) {
        InputError error = firstError(() -> service.allocate(NET, VLAN, List.of(new Requirement(10, name, 5L))));

        assertThat(error).isEqualTo(new InputError(INVALID_NAME, "vlans[0].name",
                "VLAN 10 : nom invalide : lettres (accents compris), chiffres, - et _ (32 max)"));
    }

    @Test
    void invalidNameWithoutVlansUsesTheLineNumber() {
        assertThat(firstError(() -> service.allocate(NET, REQUIREMENTS, List.of(new Requirement(null, "a;b", 5L)))).message())
                .isEqualTo("Besoin n°1 : nom invalide : lettres (accents compris), chiffres, - et _ (32 max)");
    }

    @Test
    void namesAreTrimmedAndAccentsAreAllowed() {
        VlsmPlan plan = service.allocate(NET, REQUIREMENTS, List.of(new Requirement(7, "  Comptabilité ", 5L)));

        assertThat(plan.subnets().get(0).requirement()).isEqualTo(new Requirement(null, "Comptabilité", 5L));
    }

    @Test
    void vlanNameDefaultsToVlanAndId() {
        VlsmPlan plan = service.allocate(NET, VLAN, List.of(new Requirement(10, null, 5L), new Requirement(20, " ", 5L)));

        assertThat(plan.subnets()).extracting(s -> s.requirement().name()).containsExactly("VLAN10", "VLAN20");
    }

    @Test
    void nullRowIsReportedAsMissingFields() {
        List<Requirement> rows = new ArrayList<>(Arrays.asList(new Requirement(10, null, 5L), null));

        assertThat(errors(() -> service.allocate(NET, VLAN, rows)))
                .extracting(InputError::field).containsExactly("vlans[1].id", "vlans[1].hosts");
    }

    @Test
    void collectsErrorsFromEveryField() {
        List<InputError> errors = errors(() -> service.allocate("300.1.1.1/24", VLAN,
                List.of(new Requirement(10, "a;b", 0L), new Requirement(20, null, 5L))));

        assertThat(errors).extracting(InputError::field)
                .containsExactly("network", "vlans[0].name", "vlans[0].hosts");
        assertThat(errors).extracting(InputError::code)
                .containsExactly(OCTET_OUT_OF_RANGE, INVALID_NAME, HOSTS_NOT_POSITIVE);
    }
}
