package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ipv6AddressBitsTest {

    private static Ipv6Address parse(String text) {
        return Ipv6Address.parse(text, "address");
    }

    @ParameterizedTest
    @CsvSource({
            "0,   0x0000000000000000, 0x0000000000000000",
            "1,   0x8000000000000000, 0x0000000000000000",
            "48,  0xffffffffffff0000, 0x0000000000000000",
            "63,  0xfffffffffffffffe, 0x0000000000000000",
            "64,  0xffffffffffffffff, 0x0000000000000000",
            "65,  0xffffffffffffffff, 0x8000000000000000",
            "127, 0xffffffffffffffff, 0xfffffffffffffffe",
            "128, 0xffffffffffffffff, 0xffffffffffffffff"
    })
    void buildsMasksOnBothHalves(int prefix, String high, String low) {
        Ipv6Address mask = Ipv6Address.maskOf(prefix);

        assertThat(mask.high()).isEqualTo(Long.parseUnsignedLong(high.substring(2), 16));
        assertThat(mask.low()).isEqualTo(Long.parseUnsignedLong(low.substring(2), 16));
    }

    @Test
    void everyMaskHasExactlyPrefixBitsSet() {
        for (int prefix = 0; prefix <= 128; prefix++) {
            Ipv6Address mask = Ipv6Address.maskOf(prefix);
            assertThat(Long.bitCount(mask.high()) + Long.bitCount(mask.low())).as("/%d", prefix).isEqualTo(prefix);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 129})
    void rejectsPrefixOutOfRange(int prefix) {
        assertThatThrownBy(() -> Ipv6Address.maskOf(prefix)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesBitwiseOperationsHalfByHalf() {
        Ipv6Address address = parse("2001:db8:acad:10:abcd::25");
        Ipv6Address mask = Ipv6Address.maskOf(64);

        assertThat(address.and(mask)).hasToString("2001:db8:acad:10::");
        assertThat(address.and(mask).or(mask.not())).hasToString("2001:db8:acad:10:ffff:ffff:ffff:ffff");
    }

    @Test
    void carriesFromLowToHigh() {
        assertThat(parse("::ffff:ffff:ffff:ffff").plus(Ipv6Address.ONE)).hasToString("0:0:0:1::");
        assertThat(parse("2001:db8::").plus(Ipv6Address.ONE)).hasToString("2001:db8::1");
        assertThat(parse("::8000:0:0:0").plus(parse("::8000:0:0:0"))).hasToString("0:0:0:1::");
    }

    @Test
    void shiftsAcrossTheTwoHalves() {
        assertThat(Ipv6Address.ONE.shiftLeft(0)).isEqualTo(Ipv6Address.ONE);
        assertThat(Ipv6Address.ONE.shiftLeft(62)).hasToString("::4000:0:0:0");
        assertThat(new Ipv6Address(0, 4).shiftLeft(62)).hasToString("0:0:0:1::");
        assertThat(new Ipv6Address(0, 3).shiftLeft(63)).hasToString("::1:8000:0:0:0");
        assertThat(Ipv6Address.ONE.shiftLeft(64)).hasToString("0:0:0:1::");
        assertThat(Ipv6Address.ONE.shiftLeft(127)).hasToString("8000::");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 128})
    void rejectsShiftOutOfRange(int bits) {
        assertThatThrownBy(() -> Ipv6Address.ONE.shiftLeft(bits)).isInstanceOf(IllegalArgumentException.class);
    }
}
