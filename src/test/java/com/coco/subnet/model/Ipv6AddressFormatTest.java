package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class Ipv6AddressFormatTest {

    private static Ipv6Address parse(String text) {
        return Ipv6Address.parse(text, "address");
    }

    @ParameterizedTest
    @CsvSource({
            "2001:0db8:0000:0000:0000:ff00:0042:8329, 2001:db8::ff00:42:8329",
            "2001:db8:0:0:1:0:0:1,                    2001:db8::1:0:0:1",
            "1:0:0:2:0:0:0:3,                         1:0:0:2::3",
            "2001:db8:0:1:1:1:1:1,                    2001:db8:0:1:1:1:1:1",
            "2001:DB8::A,                             2001:db8::a",
            "0:0:0:0:0:0:0:0,                         ::",
            "0:0:0:0:0:0:0:1,                         ::1",
            "fe80:0:0:0:0:0:0:0,                      fe80::",
            "1:2:3:4:5:6:7::,                         1:2:3:4:5:6:7:0",
            "'  2001:db8::1\t',                       2001:db8::1"
    })
    void formatsTheRfc5952CompressedForm(String input, String expected) {
        assertThat(parse(input)).hasToString(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"::", "::1", "2001:db8::ff00:42:8329", "2001:db8::1:0:0:1", "2001:db8::a", "fe80::",
            "ff02::1", "8000::", "1:0:0:2::", "1:0:0:2::3", "2001:db8:0:1:1:1:1:1", "1:2:3:4:5:6:7:0",
            "2001:db8:acad:10::", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"})
    void roundTripsThroughText(String text) {
        Ipv6Address address = parse(text);
        assertThat(parse(address.toString())).isEqualTo(address);
        assertThat(parse(address.toFullString())).isEqualTo(address);
    }

    @Test
    void formatsTheFullForm() {
        assertThat(parse("2001:db8::1").toFullString()).isEqualTo("2001:0db8:0000:0000:0000:0000:0000:0001");
        assertThat(parse("::").toFullString()).isEqualTo("0000:0000:0000:0000:0000:0000:0000:0000");
    }
}
