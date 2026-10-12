package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ipv4AddressBitsTest {

    private static Ipv4Address parse(String text) {
        return Ipv4Address.parse(text, "address");
    }

    @ParameterizedTest(name = "/{0} -> {1}")
    @CsvSource({
            "0,0.0.0.0", "1,128.0.0.0", "8,255.0.0.0", "16,255.255.0.0", "24,255.255.255.0",
            "25,255.255.255.128", "30,255.255.255.252", "31,255.255.255.254", "32,255.255.255.255"
    })
    void maskOfPrefix(int prefix, String mask) {
        assertThat(Ipv4Address.maskOf(prefix)).hasToString(mask);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 33})
    void maskOfRejectsAnInvalidPrefix(int prefix) {
        assertThatThrownBy(() -> Ipv4Address.maskOf(prefix)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void andOrNotComputeNetworkAndBroadcast() {
        Ipv4Address ip = parse("192.168.10.37");
        Ipv4Address mask = parse("255.255.255.0");

        assertThat(ip.and(mask)).hasToString("192.168.10.0");
        assertThat(mask.not()).hasToString("0.0.0.255");
        assertThat(ip.and(mask).or(mask.not())).hasToString("192.168.10.255");
    }

    @Test
    void toUnsignedLongIsNeverNegative() {
        assertThat(parse("200.1.1.1").toUnsignedLong()).isEqualTo(3_355_508_993L);
        assertThat(parse("255.255.255.255").toUnsignedLong()).isEqualTo(4_294_967_295L);
    }

    @Test
    void plusMovesAcrossOctetsAndTheSignBit() {
        assertThat(parse("192.168.10.255").plus(1)).hasToString("192.168.11.0");
        assertThat(parse("127.255.255.255").plus(1)).hasToString("128.0.0.0");
        assertThat(parse("255.255.255.255").plus(-1)).hasToString("255.255.255.254");
    }
}
