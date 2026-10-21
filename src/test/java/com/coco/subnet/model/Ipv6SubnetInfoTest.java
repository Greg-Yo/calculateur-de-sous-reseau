package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class Ipv6SubnetInfoTest {

    private static Ipv6SubnetInfo info(String cidr) {
        return Ipv6SubnetInfo.of(Ipv6Cidr.parse(cidr, "cidr"));
    }

    @Test
    void referenceExample() {
        Ipv6SubnetInfo info = info("2001:db8:acad:10::/64");

        assertThat(info.cidr()).hasToString("2001:db8:acad:10::/64");
        assertThat(info.networkAddress()).hasToString("2001:db8:acad:10::");
        assertThat(info.networkAddress().toFullString()).isEqualTo("2001:0db8:acad:0010:0000:0000:0000:0000");
        assertThat(info.lastAddress()).hasToString("2001:db8:acad:10:ffff:ffff:ffff:ffff");
        assertThat(info.totalAddresses()).isEqualTo(BigInteger.TWO.pow(64));
        assertThat(info.subnets64()).isEqualTo(BigInteger.ONE);
        assertThat(info.type()).isEqualTo(Ipv6AddressType.DOCUMENTATION);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "::/0           | ::           | ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff | 128 | 64    | OTHER",
            "::1/128        | ::1          | ::1                                     | 0   |       | LOOPBACK",
            "::/128         | ::           | ::                                      | 0   |       | UNSPECIFIED",
            "::ffff:0:0/96  | ::ffff:0:0   | ::ffff:ffff:ffff                        | 32  |       | IPV4_MAPPED",
            "fe80::1/64     | fe80::       | fe80::ffff:ffff:ffff:ffff               | 64  | 0     | LINK_LOCAL",
            "fd00:1234::/48 | fd00:1234::  | fd00:1234:0:ffff:ffff:ffff:ffff:ffff    | 80  | 16    | UNIQUE_LOCAL",
            "2001:db8::/127 | 2001:db8::   | 2001:db8::1                             | 1   |       | DOCUMENTATION",
            "2a00:1450::/32 | 2a00:1450::  | 2a00:1450:ffff:ffff:ffff:ffff:ffff:ffff | 96  | 32    | GLOBAL_UNICAST",
            "ff02::1/128    | ff02::1      | ff02::1                                 | 0   |       | MULTICAST",
            "fc00::/6       | fc00::       | ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff | 122 | 58    | OTHER"
    })
    void otherCases(String cidr, String network, String last, int totalPower, Integer subnets64Power, Ipv6AddressType type) {
        Ipv6SubnetInfo info = info(cidr);

        assertThat(info.networkAddress()).hasToString(network);
        assertThat(info.lastAddress()).hasToString(last);
        assertThat(info.totalAddresses()).isEqualTo(BigInteger.TWO.pow(totalPower));
        assertThat(info.subnets64()).isEqualTo(subnets64Power == null ? null : BigInteger.TWO.pow(subnets64Power));
        assertThat(info.type()).isEqualTo(type);
        assertThat(type.label()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "::/127          | OTHER",
            "::/96           | OTHER",
            "::ffff:0:0/95   | OTHER",
            "::ffff:0:0/97   | IPV4_MAPPED",
            "2001:db8::/31   | GLOBAL_UNICAST",
            "2001:db9::/32   | GLOBAL_UNICAST",
            "2000::/2        | OTHER",
            "fc00::/7        | UNIQUE_LOCAL",
            "fe80::/9        | OTHER",
            "fec0::/10       | OTHER",
            "ff00::/7        | OTHER",
            "ff00::/8        | MULTICAST"
    })
    void typeRequiresTheWholePrefixInsideTheRange(String cidr, Ipv6AddressType type) {
        assertThat(info(cidr).type()).isEqualTo(type);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "UNSPECIFIED    | Adresse non spécifiée",
            "LOOPBACK       | Boucle locale",
            "IPV4_MAPPED    | IPv4 mappée",
            "DOCUMENTATION  | Documentation (exemples)",
            "GLOBAL_UNICAST | Globale (routable sur Internet)",
            "UNIQUE_LOCAL   | Unique locale (privée)",
            "LINK_LOCAL     | Lien local",
            "MULTICAST      | Multicast",
            "OTHER          | Autre / réservée"
    })
    void labels(Ipv6AddressType type, String label) {
        assertThat(type.label()).isEqualTo(label);
    }
}
