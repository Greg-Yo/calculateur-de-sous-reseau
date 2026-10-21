package com.coco.subnet.service;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import com.coco.subnet.model.Ipv6Plan;
import com.coco.subnet.model.Ipv6Requirement;
import com.coco.subnet.model.PlanMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static com.coco.subnet.TestErrors.errors;
import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class Ipv6PlanServiceValidationTest {

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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2001:db8::/48 | 40  | La taille des sous-réseaux doit être comprise entre /49 et /127",
            "2001:db8::/48 | 48  | La taille des sous-réseaux doit être comprise entre /49 et /127",
            "2001:db8::/48 | 128 | La taille des sous-réseaux doit être comprise entre /49 et /127",
            "abc           | 0   | La taille des sous-réseaux doit être comprise entre /1 et /127",
            "2001:db8::/127| 128 | La taille des sous-réseaux doit être comprise entre /1 et /127"
    })
    void rejectsASubnetPrefixOutOfRange(String network, int subnetPrefix, String message) {
        List<InputError> errors = errors(() -> service.allocate(network, subnetPrefix, PlanMode.VLAN, vlans(1)));

        assertThat(errors).filteredOn(e -> e.field().equals("subnetPrefix"))
                .extracting(InputError::code, InputError::message)
                .containsExactly(tuple(ErrorCode.SUBNET_PREFIX_OUT_OF_RANGE, message));
    }

    @Test
    void rejectsAMissingSubnetPrefix() {
        InputError error = firstError(() -> service.allocate("2001:db8::/48", null, PlanMode.VLAN, vlans(1)));

        assertThat(error.code()).isEqualTo(ErrorCode.MISSING_FIELD);
        assertThat(error.field()).isEqualTo("subnetPrefix");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2001:db8::/127", "2001:db8::1/128"})
    void rejectsAParentThatCannotBeSplit(String network) {
        InputError error = firstError(() -> service.allocate(network, 127, PlanMode.VLAN, vlans(1)));

        assertThat(error.code()).isEqualTo(ErrorCode.PARENT_NOT_SPLITTABLE);
        assertThat(error.message()).isEqualTo("Un /127 ou /128 ne peut pas être découpé");
    }

    @Test
    void reportsTooSmallNetworks() {
        InputError error = firstError(() -> service.allocate("2001:db8:acad::/62", 64, PlanMode.VLAN, vlans(5)));

        assertThat(error.code()).isEqualTo(ErrorCode.NETWORK_TOO_SMALL);
        assertThat(error.field()).isEqualTo("network");
        assertThat(error.message()).isEqualTo("Le /62 contient 4 sous-réseaux /64 : 5 demandés, il en manque 1");
    }

    @Test
    void reportsAllFieldErrorsAtOnce() {
        List<Ipv6Requirement> rows = new ArrayList<>();
        rows.add(new Ipv6Requirement(0, "A"));
        rows.add(new Ipv6Requirement(null, "B"));
        rows.add(new Ipv6Requirement(4095, "-C"));
        rows.add(null);
        List<InputError> errors = errors(() -> service.allocate("2001:db8::/129", 64, PlanMode.VLAN, rows));

        assertThat(errors).extracting(InputError::field, InputError::code).containsExactly(
                tuple("network", ErrorCode.PREFIX_OUT_OF_RANGE),
                tuple("vlans[0].id", ErrorCode.VLAN_ID_OUT_OF_RANGE),
                tuple("vlans[1].id", ErrorCode.MISSING_FIELD),
                tuple("vlans[2].id", ErrorCode.VLAN_ID_OUT_OF_RANGE),
                tuple("vlans[2].name", ErrorCode.INVALID_NAME),
                tuple("vlans[3].id", ErrorCode.MISSING_FIELD));
        assertThat(errors.get(4).message()).isEqualTo("Ligne 3 : nom invalide : lettres (accents compris), chiffres, - et _ (32 max)");
    }

    @Test
    void namesAreRequiredWithoutVlans() {
        List<InputError> errors = errors(() -> service.allocate("2001:db8::/48", 64, PlanMode.REQUIREMENTS,
                Arrays.asList(new Ipv6Requirement(null, " "), new Ipv6Requirement(null, "a;b"))));

        assertThat(errors).extracting(InputError::field, InputError::code, InputError::message).containsExactly(
                tuple("subnets[0].name", ErrorCode.EMPTY_NAME, "Le besoin n°1 doit avoir un nom"),
                tuple("subnets[1].name", ErrorCode.INVALID_NAME,
                        "Besoin n°2 : nom invalide : lettres (accents compris), chiffres, - et _ (32 max)"));
    }

    @Test
    void rejectsEmptyAndTooLongLists() {
        assertThat(firstError(() -> service.allocate("2001:db8::/48", 64, PlanMode.VLAN, List.of())).code())
                .isEqualTo(ErrorCode.NO_REQUIREMENTS);
        assertThat(firstError(() -> service.allocate("2001:db8::/48", 64, PlanMode.REQUIREMENTS, null)).field())
                .isEqualTo("subnets");
        assertThat(firstError(() -> service.allocate("2001:db8::/48", 64, PlanMode.VLAN, vlans(101))).code())
                .isEqualTo(ErrorCode.TOO_MANY_REQUIREMENTS);
    }

    @Test
    void reportsDuplicates() {
        List<InputError> vlanErrors = errors(() -> service.allocate("2001:db8::/48", 64, PlanMode.VLAN,
                List.of(new Ipv6Requirement(10, "A"), new Ipv6Requirement(10, "B"), new Ipv6Requirement(20, "a"))));
        List<InputError> nameErrors = errors(() -> service.allocate("2001:db8::/48", 64, PlanMode.REQUIREMENTS,
                named("RH", "rh")));

        assertThat(vlanErrors).extracting(InputError::field, InputError::code).containsExactly(
                tuple("vlans[1].id", ErrorCode.DUPLICATE_VLAN_ID), tuple("vlans[2].name", ErrorCode.DUPLICATE_NAME));
        assertThat(nameErrors).extracting(InputError::message).containsExactly("Le nom 'rh' est utilisé deux fois");
    }

    @Test
    void reportsSubnetPrefixAndRowErrorsTogetherWhenTheParentIsValid() {
        List<Ipv6Requirement> rows = Arrays.asList(new Ipv6Requirement(10, null), new Ipv6Requirement(10, null), null);
        List<InputError> errors = errors(() -> service.allocate("2001:db8::/48", 200, PlanMode.VLAN, rows));

        // Le doublon du VLAN 10 n'est signalé que lorsque tous les champs sont valides
        assertThat(errors).extracting(InputError::field, InputError::code).containsExactly(
                tuple("subnetPrefix", ErrorCode.SUBNET_PREFIX_OUT_OF_RANGE),
                tuple("vlans[2].id", ErrorCode.MISSING_FIELD));
    }

    @Test
    void acceptsAccentsAndThirtyTwoCharacters() {
        Ipv6Plan plan = service.allocate("2001:db8::/48", 64, PlanMode.REQUIREMENTS,
                named("Comptabilité", " Écoles_2 ", "a".repeat(32)));
        Ipv6Plan vlanPlan = service.allocate("2001:db8::/48", 64, PlanMode.VLAN,
                List.of(new Ipv6Requirement(10, "   ")));

        assertThat(plan.subnets()).extracting(s -> s.requirement().name())
                .containsExactly("Comptabilité", "Écoles_2", "a".repeat(32));
        assertThat(cidrs(plan)).containsExactly("2001:db8::/64", "2001:db8:0:1::/64", "2001:db8:0:2::/64");
        assertThat(vlanPlan.subnets().get(0).requirement().name()).isEqualTo("VLAN10");
    }

    @Test
    void rejectsThirtyThreeCharacters() {
        InputError error = firstError(() -> service.allocate("2001:db8::/48", 64, PlanMode.REQUIREMENTS,
                named("a".repeat(33))));

        assertThat(error.code()).isEqualTo(ErrorCode.INVALID_NAME);
        assertThat(error.field()).isEqualTo("subnets[0].name");
    }

    @Test
    void acceptsOneHundredSubnets() {
        List<Ipv6Requirement> rows = IntStream.range(0, 100).mapToObj(i -> new Ipv6Requirement(null, "N" + i)).toList();
        Ipv6Plan plan = service.allocate("2001:db8::/48", 64, PlanMode.REQUIREMENTS, rows);

        // n° 99 = 0x63
        assertThat(plan.subnets().get(99).cidr()).hasToString("2001:db8:0:63::/64");
    }
}
