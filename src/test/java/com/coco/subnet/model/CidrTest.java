package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CidrTest {

    private static Cidr parse(String text) {
        return Cidr.parse(text, "cidr");
    }

    @Test
    void parsesAddressAndPrefix() {
        Cidr cidr = parse("192.168.10.0/24");

        assertThat(cidr.address()).hasToString("192.168.10.0");
        assertThat(cidr.prefix()).isEqualTo(24);
        assertThat(cidr).hasToString("192.168.10.0/24");
    }

    @Test
    void acceptsCopyPastedTrailingWhitespace() {
        assertThat(parse("192.168.1.0/24\n")).hasToString("192.168.1.0/24");
        assertThat(parse("\t10.0.0.0/8  ")).hasToString("10.0.0.0/8");
    }

    @Test
    void toleratesALeadingZeroInThePrefix() {
        assertThat(parse("10.0.0.0/024").prefix()).isEqualTo(24);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "''|EMPTY_INPUT",
            "/24|EMPTY_INPUT",
            "192.168.1.0|MISSING_PREFIX",
            "abc|MISSING_PREFIX",
            "1.2.3.4/24/8|MULTIPLE_SLASHES",
            "1.2.3.4/abc|INVALID_PREFIX",
            "1.2.3.4/|INVALID_PREFIX",
            "1.2.3.4/2.5|INVALID_PREFIX",
            "1.2.3.4/-1|INVALID_PREFIX",
            "1.2.3.4/+8|INVALID_PREFIX",
            "1.2.3.4/0024|INVALID_PREFIX",
            "192.168.1.0/2 4|INVALID_PREFIX",
            "1.2.3.4/33|PREFIX_OUT_OF_RANGE",
            "1.2.3.4/999|PREFIX_OUT_OF_RANGE",
            "300.1.1.1/24|OCTET_OUT_OF_RANGE",
            "1.2.3.4 /24|INVALID_CHARACTERS",
            "192.168.1.0\t/24|INVALID_CHARACTERS"
    })
    void rejectsInvalidCidr(String input, ErrorCode expected) {
        assertThat(firstError(() -> parse(input)).code()).isEqualTo(expected);
    }

    @Test
    void nullIsAnEmptyInput() {
        assertThat(firstError(() -> parse(null)).code()).isEqualTo(ErrorCode.EMPTY_INPUT);
    }

    @Test
    void messagesAreInFrench() {
        assertThat(firstError(() -> parse("192.168.1.0")).message()).isEqualTo("Préfixe manquant : format attendu a.b.c.d/n");
        assertThat(firstError(() -> parse("1.2.3.4/24/8")).message()).isEqualTo("Un seul '/' autorisé");
        assertThat(firstError(() -> parse("1.2.3.4/abc")).message()).isEqualTo("Le préfixe doit être un nombre entier positif");
        assertThat(firstError(() -> parse("1.2.3.4/33")).message()).isEqualTo("Le préfixe doit être compris entre 0 et 32");
    }

    @Test
    void computesTheNetworkOfAHostAddress() {
        Cidr cidr = parse("192.168.10.37/24");

        assertThat(cidr.mask()).hasToString("255.255.255.0");
        assertThat(cidr.networkAddress()).hasToString("192.168.10.0");
        assertThat(cidr.isNetworkAddress()).isFalse();
        assertThat(cidr.toNetwork()).hasToString("192.168.10.0/24");
        assertThat(cidr.correctionWarning()).contains("Adresse corrigée vers le réseau 192.168.10.0/24");
    }

    @Test
    void aNetworkAddressNeedsNoCorrection() {
        Cidr cidr = parse("192.168.10.0/24");

        assertThat(cidr.isNetworkAddress()).isTrue();
        assertThat(cidr.correctionWarning()).isEmpty();
    }

    @Test
    void sizeIsALong() {
        assertThat(parse("0.0.0.0/0").size()).isEqualTo(4_294_967_296L);
        assertThat(parse("10.0.0.0/8").size()).isEqualTo(16_777_216L);
        assertThat(parse("10.0.0.1/32").size()).isEqualTo(1L);
    }

    @Test
    void constructorRejectsAnImpossiblePrefix() {
        Ipv4Address address = Ipv4Address.parse("10.0.0.0", "a");
        assertThatThrownBy(() -> new Cidr(address, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cidr(address, 33)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cidr(null, 24)).isInstanceOf(NullPointerException.class);
    }
}
