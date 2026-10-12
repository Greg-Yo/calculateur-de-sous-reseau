package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SubnetInfoTest {

    private static SubnetInfo of(String cidr) {
        return SubnetInfo.of(Cidr.parse(cidr, "cidr"));
    }

    @Test
    void referenceExample() {
        SubnetInfo info = of("192.168.10.0/24");

        assertThat(info.cidr()).hasToString("192.168.10.0/24");
        assertThat(info.networkAddress()).hasToString("192.168.10.0");
        assertThat(info.subnetMask()).hasToString("255.255.255.0");
        assertThat(info.wildcardMask()).hasToString("0.0.0.255");
        assertThat(info.broadcastAddress()).hasToString("192.168.10.255");
        assertThat(info.firstUsable()).hasToString("192.168.10.1");
        assertThat(info.lastUsable()).hasToString("192.168.10.254");
        assertThat(info.usableHosts()).isEqualTo(254);
        assertThat(info.totalAddresses()).isEqualTo(256);
    }

    @ParameterizedTest(name = "/{0} -> {1}, {2} hôtes")
    @CsvSource({
            "0,0.0.0.0,4294967294", "1,128.0.0.0,2147483646", "2,192.0.0.0,1073741822",
            "3,224.0.0.0,536870910", "4,240.0.0.0,268435454", "5,248.0.0.0,134217726",
            "6,252.0.0.0,67108862", "7,254.0.0.0,33554430", "8,255.0.0.0,16777214",
            "9,255.128.0.0,8388606", "10,255.192.0.0,4194302", "11,255.224.0.0,2097150",
            "12,255.240.0.0,1048574", "13,255.248.0.0,524286", "14,255.252.0.0,262142",
            "15,255.254.0.0,131070", "16,255.255.0.0,65534", "17,255.255.128.0,32766",
            "18,255.255.192.0,16382", "19,255.255.224.0,8190", "20,255.255.240.0,4094",
            "21,255.255.248.0,2046", "22,255.255.252.0,1022", "23,255.255.254.0,510",
            "24,255.255.255.0,254", "25,255.255.255.128,126", "26,255.255.255.192,62",
            "27,255.255.255.224,30", "28,255.255.255.240,14", "29,255.255.255.248,6",
            "30,255.255.255.252,2", "31,255.255.255.254,2", "32,255.255.255.255,1"
    })
    void everyPrefix(int prefix, String mask, long usableHosts) {
        SubnetInfo info = of("10.0.0.0/" + prefix);

        assertThat(info.subnetMask()).hasToString(mask);
        assertThat(info.usableHosts()).isEqualTo(usableHosts);
        assertThat(info.totalAddresses()).isEqualTo(1L << (32 - prefix));
    }

    @Test
    void wholeInternet() {
        SubnetInfo info = of("0.0.0.0/0");

        assertThat(info.broadcastAddress()).hasToString("255.255.255.255");
        assertThat(info.firstUsable()).hasToString("0.0.0.1");
        assertThat(info.lastUsable()).hasToString("255.255.255.254");
        assertThat(info.totalAddresses()).isEqualTo(4_294_967_296L);
    }

    @Test
    void slash32IsASingleHost() {
        SubnetInfo info = of("255.255.255.255/32");

        assertThat(info.networkAddress()).hasToString("255.255.255.255");
        assertThat(info.broadcastAddress()).hasToString("255.255.255.255");
        assertThat(info.firstUsable()).hasToString("255.255.255.255");
        assertThat(info.lastUsable()).hasToString("255.255.255.255");
        assertThat(info.usableHosts()).isEqualTo(1);
    }

    @Test
    void slash31IsAPointToPointLink() {
        SubnetInfo info = of("10.0.0.0/31");

        assertThat(info.firstUsable()).hasToString("10.0.0.0");
        assertThat(info.lastUsable()).hasToString("10.0.0.1");
        assertThat(info.usableHosts()).isEqualTo(2);
    }

    @Test
    void hostAddressIsCorrectedToItsNetwork() {
        SubnetInfo info = of("192.168.10.37/24");

        assertThat(info.cidr()).hasToString("192.168.10.0/24");
        assertThat(info.networkAddress()).hasToString("192.168.10.0");
    }

    @Test
    void highAddressesSurviveTheSignedIntTrap() {
        SubnetInfo info = of("200.1.1.1/16");

        assertThat(info.networkAddress()).hasToString("200.1.0.0");
        assertThat(info.broadcastAddress()).hasToString("200.1.255.255");
        assertThat(info.lastUsable()).hasToString("200.1.255.254");
    }
}
