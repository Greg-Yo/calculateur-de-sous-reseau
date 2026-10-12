package com.coco.subnet.service;

import com.coco.subnet.model.InputError;
import com.coco.subnet.model.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static com.coco.subnet.TestErrors.errors;
import static com.coco.subnet.TestErrors.firstError;
import static com.coco.subnet.model.ErrorCode.HOSTS_NOT_POSITIVE;
import static com.coco.subnet.model.ErrorCode.HOSTS_TOO_LARGE;
import static com.coco.subnet.model.ErrorCode.MISSING_FIELD;
import static com.coco.subnet.model.ErrorCode.VLAN_COUNT_OUT_OF_RANGE;
import static org.assertj.core.api.Assertions.assertThat;

class VlsmServiceGenerationTest {

    private final VlsmService service = new VlsmService();

    @Test
    void generatesDetailedRows() {
        assertThat(service.generateUniformVlans(4, 30L)).containsExactly(
                new Requirement(10, "VLAN10", 30L), new Requirement(20, "VLAN20", 30L),
                new Requirement(30, "VLAN30", 30L), new Requirement(40, "VLAN40", 30L));
    }

    @Test
    void hundredVlansEndAtId1000() {
        List<Requirement> vlans = service.generateUniformVlans(100, 1L);

        assertThat(vlans).hasSize(100);
        assertThat(vlans.get(99).vlanId()).isEqualTo(1000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void vlanCountOutOfRange(int count) {
        assertThat(firstError(() -> service.generateUniformVlans(count, 30L)))
                .isEqualTo(new InputError(VLAN_COUNT_OUT_OF_RANGE, "vlanCount", "Le nombre de VLAN doit être entre 1 et 100"));
    }

    @Test
    void bothFieldsAreRequiredAndReportedTogether() {
        assertThat(errors(() -> service.generateUniformVlans(null, null))).containsExactly(
                new InputError(MISSING_FIELD, "vlanCount", "Le champ « Nombre de VLAN » est obligatoire"),
                new InputError(MISSING_FIELD, "hostsPerVlan", "Le champ « Hôtes par VLAN » est obligatoire"));
    }

    @Test
    void hostsPerVlanMustBeValid() {
        assertThat(firstError(() -> service.generateUniformVlans(4, 0L)))
                .isEqualTo(new InputError(HOSTS_NOT_POSITIVE, "hostsPerVlan", "Au moins 1 hôte requis"));
        assertThat(firstError(() -> service.generateUniformVlans(4, 4_294_967_294L)))
                .isEqualTo(new InputError(HOSTS_TOO_LARGE, "hostsPerVlan",
                        "Un sous-réseau IPv4 ne peut pas dépasser 4 294 967 293 hôtes (hors passerelle)"));
    }

    @Test
    void nextVlanId() {
        assertThat(service.nextVlanId(List.of())).isEqualTo(10);
        assertThat(service.nextVlanId(List.of(10, 40, 20))).isEqualTo(50);
        assertThat(service.nextVlanId(Arrays.asList(null, 30, null))).isEqualTo(40);
    }
}
