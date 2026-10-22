package com.coco.subnet.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.coco.subnet.TestErrors.firstError;
import static org.assertj.core.api.Assertions.assertThat;

class Ipv6AddressParseTest {

    private static Ipv6Address parse(String text) {
        return Ipv6Address.parse(text, "address");
    }

    @Test
    void parsesGroupsIntoTwoLongs() {
        Ipv6Address address = parse("2001:db8:acad:10::1");

        assertThat(address.high()).isEqualTo(0x20010db8acad0010L);
        assertThat(address.low()).isEqualTo(1L);
    }

    @Test
    void highHalfIsNegativeWhenTheFirstBitIsSet() {
        assertThat(parse("ff02::1").high()).isNegative();
        assertThat(parse("8000::").high()).isEqualTo(Long.MIN_VALUE);
        assertThat(parse("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")).isEqualTo(new Ipv6Address(-1L, -1L));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''                        | EMPTY_INPUT           | L'adresse est obligatoire",
            "'   '                     | EMPTY_INPUT           | L'adresse est obligatoire",
            "2001:db8::g               | INVALID_CHARACTERS    | Caractère non autorisé : 'g'",
            "2001 :db8::1              | INVALID_CHARACTERS    | Caractère non autorisé : ' '",
            "２００１::1                | INVALID_CHARACTERS    | Caractère non autorisé : '２'",
            "::ffff:192.168.1.1        | INVALID_CHARACTERS    | Caractère non autorisé : '.' (la notation IPv4 intégrée n'est pas prise en charge)",
            "192.168.1.0               | INVALID_CHARACTERS    | C'est une adresse IPv4 : utilisez le calcul CIDR IPv4 (page CIDR ou /api/subnets)",
            "1.2.3                     | INVALID_CHARACTERS    | Caractère non autorisé : '.' (la notation IPv4 intégrée n'est pas prise en charge)",
            "fe80::1%eth0              | INVALID_CHARACTERS    | Caractère non autorisé : '%' (les identifiants de zone ne sont pas pris en charge)",
            "2001::db8::1              | MULTIPLE_DOUBLE_COLON | « :: » ne peut apparaître qu'une seule fois",
            "1:::2                     | MULTIPLE_DOUBLE_COLON | « :: » ne peut apparaître qu'une seule fois",
            ":1:2:3:4:5:6:7            | EMPTY_GROUP           | Groupe vide : un ':' seul est interdit en début ou en fin d'adresse",
            "1:2:3:4:5:6:7:            | EMPTY_GROUP           | Groupe vide : un ':' seul est interdit en début ou en fin d'adresse",
            "2001::db8:                | EMPTY_GROUP           | Groupe vide : un ':' seul est interdit en début ou en fin d'adresse",
            "2001:db8:1:2:3:4:5        | WRONG_GROUP_COUNT     | Une adresse IPv6 contient 8 groupes (7 trouvés)",
            "1:2:3:4:5:6:7:8:9         | WRONG_GROUP_COUNT     | Une adresse IPv6 contient 8 groupes (9 trouvés)",
            "1:2:3:4::5:6:7:8          | WRONG_GROUP_COUNT     | Avec « :: », au plus 7 groupes peuvent être écrits (8 trouvés)",
            "2001:0db8a::1             | GROUP_TOO_LONG        | Le groupe '0db8a' dépasse 4 chiffres hexadécimaux",
            "1:2:3:4:5:6:7:99999999999 | GROUP_TOO_LONG        | Le groupe '99999999999' dépasse 4 chiffres hexadécimaux"
    })
    void rejectsInvalidInputWithAPreciseCode(String input, ErrorCode code, String message) {
        InputError error = firstError(() -> parse(input));

        assertThat(error.code()).isEqualTo(code);
        assertThat(error.field()).isEqualTo("address");
        assertThat(error.message()).isEqualTo(message);
    }

    @Test
    void rejectsNull() {
        assertThat(firstError(() -> parse(null)).code()).isEqualTo(ErrorCode.EMPTY_INPUT);
    }
}
