package com.coco.subnet.service;

import com.coco.subnet.model.CidrResult;
import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.InputError;
import org.junit.jupiter.api.Test;

import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;

class SubnetServiceTest {

    private final SubnetService service = new SubnetService();

    @Test
    void networkAddressHasNoWarning() {
        CidrResult result = service.calculate("192.168.10.0/24");

        assertThat(result.subnet().broadcastAddress()).hasToString("192.168.10.255");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void hostAddressIsCorrectedWithAWarning() {
        CidrResult result = service.calculate("192.168.10.37/24");

        assertThat(result.subnet().cidr()).hasToString("192.168.10.0/24");
        assertThat(result.warnings()).containsExactly("Adresse corrigée vers le réseau 192.168.10.0/24");
    }

    @Test
    void errorsUseTheCidrField() {
        InputError error = firstError(() -> service.calculate("300.1.1.1/24"));

        assertThat(error.code()).isEqualTo(ErrorCode.OCTET_OUT_OF_RANGE);
        assertThat(error.field()).isEqualTo("cidr");
    }
}
