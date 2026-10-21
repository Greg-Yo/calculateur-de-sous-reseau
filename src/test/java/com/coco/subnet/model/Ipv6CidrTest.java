package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ipv6CidrTest {

    private static Ipv6Cidr parse(String text) {
        return Ipv6Cidr.parse(text, "cidr");
    }

    @Test
    void parsesAddressAndPrefix() {
        Ipv6Cidr cidr = parse("  2001:db8:acad:10::/64 ");

        assertThat(cidr.address()).hasToString("2001:db8:acad:10::");
        assertThat(cidr.prefix()).isEqualTo(64);
        assertThat(cidr).hasToString("2001:db8:acad:10::/64");
    }

    @Test
    void computesNetworkLastAddressAndSize() {
        Ipv6Cidr cidr = parse("2001:db8:acad:10::25/64");

        assertThat(cidr.networkAddress()).hasToString("2001:db8:acad:10::");
        assertThat(cidr.lastAddress()).hasToString("2001:db8:acad:10:ffff:ffff:ffff:ffff");
        assertThat(cidr.size()).isEqualTo(new BigInteger("18446744073709551616"));
        assertThat(cidr.isNetworkAddress()).isFalse();
        assertThat(cidr.toNetwork()).hasToString("2001:db8:acad:10::/64");
        assertThat(cidr.correctionWarning()).contains("Adresse corrigée vers le réseau 2001:db8:acad:10::/64");
    }

    @Test
    void noWarningOnANetworkAddress() {
        assertThat(parse("2001:db8::/32").correctionWarning()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0, 128", "1, 127", "63, 65", "64, 64", "65, 63", "127, 1", "128, 0"})
    void sizeIsTwoToTheHostBits(int prefix, int hostBits) {
        assertThat(new Ipv6Cidr(Ipv6Address.ONE, prefix).size()).isEqualTo(BigInteger.TWO.pow(hostBits));
    }

    @Test
    void containsOnlyWholeBlocks() {
        Ipv6Cidr global = parse("2000::/3");

        assertThat(global.contains(parse("2001:db8::/32"))).isTrue();
        assertThat(global.contains(parse("2000::/3"))).isTrue();
        assertThat(global.contains(parse("::/2"))).isFalse();
        assertThat(global.contains(parse("fe80::/10"))).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''                 | EMPTY_INPUT         | L'adresse est obligatoire",
            "/64                | EMPTY_INPUT         | L'adresse est obligatoire",
            "2001:db8::         | MISSING_PREFIX      | Préfixe manquant : format attendu adresse/n, par exemple 2001:db8::/32",
            "2001:db8::/32/64   | MULTIPLE_SLASHES    | Un seul '/' autorisé",
            "2001:db8:: /32     | INVALID_CHARACTERS  | Caractère non autorisé : ' '",
            "2001:db8::/abc     | INVALID_PREFIX      | Le préfixe doit être un nombre entier positif",
            "2001:db8::/        | INVALID_PREFIX      | Le préfixe doit être un nombre entier positif",
            "2001:db8::/-1      | INVALID_PREFIX      | Le préfixe doit être un nombre entier positif",
            "2001:db8::/129     | PREFIX_OUT_OF_RANGE | Le préfixe doit être compris entre 0 et 128",
            "2001:db8::/999     | PREFIX_OUT_OF_RANGE | Le préfixe doit être compris entre 0 et 128"
    })
    void rejectsInvalidInput(String input, ErrorCode code, String message) {
        InputError error = firstError(() -> parse(input));

        assertThat(error.code()).isEqualTo(code);
        assertThat(error.field()).isEqualTo("cidr");
        assertThat(error.message()).isEqualTo(message);
    }

    @Test
    void rejectsNull() {
        assertThat(firstError(() -> parse(null)).code()).isEqualTo(ErrorCode.EMPTY_INPUT);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 129})
    void constructorRejectsPrefixOutOfRange(int prefix) {
        assertThatThrownBy(() -> new Ipv6Cidr(Ipv6Address.ONE, prefix)).isInstanceOf(IllegalArgumentException.class);
    }
}
