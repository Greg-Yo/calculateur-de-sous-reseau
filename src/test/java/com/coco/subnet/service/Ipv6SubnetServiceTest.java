package com.coco.subnet.service;

import com.coco.subnet.model.ErrorCode;
import com.coco.subnet.model.Ipv6CidrResult;
import org.junit.jupiter.api.Test;

import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;

class Ipv6SubnetServiceTest {

    private final Ipv6SubnetService service = new Ipv6SubnetService();

    @Test
    void calculatesWithoutWarningOnANetworkAddress() {
        Ipv6CidrResult result = service.calculate("2001:db8:acad:10::/64");

        assertThat(result.subnet().cidr()).hasToString("2001:db8:acad:10::/64");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void correctsAHostAddressWithAWarning() {
        Ipv6CidrResult result = service.calculate("fe80::1/64");

        assertThat(result.subnet().cidr()).hasToString("fe80::/64");
        assertThat(result.warnings()).containsExactly("Adresse corrigée vers le réseau fe80::/64");
    }

    @Test
    void reportsErrorsOnTheCidrField() {
        assertThat(firstError(() -> service.calculate("2001:db8::/129")).code()).isEqualTo(ErrorCode.PREFIX_OUT_OF_RANGE);
        assertThat(firstError(() -> service.calculate("2001:db8::/129")).field()).isEqualTo("cidr");
    }
}
