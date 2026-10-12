package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;

class Ipv4AddressParseTest {

    private static Ipv4Address parse(String text) {
        return Ipv4Address.parse(text, "address");
    }

    @Test
    void parsesDottedDecimalIntoA32BitValue() {
        Ipv4Address address = parse("192.168.10.1");

        assertThat(address.value()).isEqualTo(0xC0A80A01);
        assertThat(address).hasToString("192.168.10.1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "10.0.0.1", "127.0.0.1", "200.1.1.1", "255.255.255.255"})
    void formatsBackToTheSameText(String text) {
        assertThat(parse(text)).hasToString(text);
    }

    @Test
    void highAddressesAreNegativeAsSignedInt() {
        assertThat(parse("255.255.255.255").value()).isEqualTo(-1);
        assertThat(parse("200.1.1.1").value()).isNegative();
    }

    @Test
    void stripsSurroundingWhitespace() {
        assertThat(parse("  10.0.0.1 \n")).hasToString("10.0.0.1");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "''|EMPTY_INPUT",
            "'   '|EMPTY_INPUT",
            "192.168.a.1|INVALID_CHARACTERS",
            "1,2,3,4|INVALID_CHARACTERS",
            "1.2.3.-4|INVALID_CHARACTERS",
            "192.168. 1.1|INVALID_CHARACTERS",
            "192.168.1.\t1|INVALID_CHARACTERS",
            "１９２.168.1.1|INVALID_CHARACTERS",
            "192..1.1|EMPTY_OCTET",
            ".1.2.3|EMPTY_OCTET",
            "1.2.3.4.|EMPTY_OCTET",
            "192.168.1|WRONG_OCTET_COUNT",
            "1.2.3.4.5|WRONG_OCTET_COUNT",
            "01.2.3.4|LEADING_ZERO",
            "1.2.3.00|LEADING_ZERO",
            "300.01.1.1|LEADING_ZERO",
            "300.1.1.1|OCTET_OUT_OF_RANGE",
            "1.2.3.256|OCTET_OUT_OF_RANGE",
            "1.2.3.99999999999|OCTET_OUT_OF_RANGE"
    })
    void rejectsInvalidInputWithTheExpectedCode(String input, ErrorCode expected) {
        assertThat(firstError(() -> parse(input)).code()).isEqualTo(expected);
    }

    @Test
    void nullIsAnEmptyInput() {
        assertThat(firstError(() -> parse(null)).code()).isEqualTo(ErrorCode.EMPTY_INPUT);
    }

    @Test
    void messagesAreInFrench() {
        assertThat(firstError(() -> parse("")).message()).isEqualTo("L'adresse est obligatoire");
        assertThat(firstError(() -> parse("192.168.a.1")).message()).isEqualTo("Caractère non autorisé : 'a'");
        assertThat(firstError(() -> parse("192..1.1")).message()).isEqualTo("Octet vide en position 2");
        assertThat(firstError(() -> parse("1.2.3.4.")).message()).isEqualTo("Octet vide en position 5");
        assertThat(firstError(() -> parse("192.168.1")).message()).isEqualTo("Une adresse IPv4 contient 4 octets (3 trouvés)");
        assertThat(firstError(() -> parse("01.2.3.4")).message()).isEqualTo("Zéro initial interdit dans '01' (ambigu : octal)");
        assertThat(firstError(() -> parse("300.1.1.1")).message()).isEqualTo("L'octet 300 est hors limites (0–255)");
    }

    @Test
    void errorCarriesTheFieldName() {
        assertThat(firstError(() -> Ipv4Address.parse("x", "network")).field()).isEqualTo("network");
    }

    @Test
    void strictParsingDoesNotTrim() {
        assertThat(firstError(() -> Ipv4Address.parseStrict(" 10.0.0.1", "address")).code()).isEqualTo(ErrorCode.INVALID_CHARACTERS);
        assertThat(firstError(() -> Ipv4Address.parseStrict("", "address")).code()).isEqualTo(ErrorCode.EMPTY_INPUT);
        assertThat(Ipv4Address.parseStrict("10.0.0.1", "address")).hasToString("10.0.0.1");
    }
}
