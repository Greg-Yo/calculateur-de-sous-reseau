package com.coco.subnet.model;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

import static com.coco.subnet.model.ErrorCode.EMPTY_INPUT;
import static com.coco.subnet.model.ErrorCode.INVALID_PREFIX;
import static com.coco.subnet.model.ErrorCode.MISSING_PREFIX;
import static com.coco.subnet.model.ErrorCode.MULTIPLE_SLASHES;
import static com.coco.subnet.model.ErrorCode.PREFIX_OUT_OF_RANGE;

/** Une adresse IPv6 et un préfixe, par exemple 2001:db8:acad::/48. */
public record Ipv6Cidr(Ipv6Address address, int prefix) {

    public Ipv6Cidr {
        Objects.requireNonNull(address, "address");
        if (prefix < 0 || prefix > 128) {
            throw new IllegalArgumentException("Préfixe hors limites : " + prefix);
        }
    }

    /** Ordre des vérifications : vide → nombre de '/' → adresse → préfixe (comme Cidr). */
    public static Ipv6Cidr parse(String input, String field) {
        if (input == null || input.isBlank()) {
            throw InvalidInputException.of(EMPTY_INPUT, field, "L'adresse est obligatoire");
        }
        String text = input.strip();
        int slash = text.indexOf('/');
        if (slash < 0) {
            throw InvalidInputException.of(MISSING_PREFIX, field,
                    "Préfixe manquant : format attendu adresse/n, par exemple 2001:db8::/32");
        }
        if (text.indexOf('/', slash + 1) >= 0) {
            throw InvalidInputException.of(MULTIPLE_SLASHES, field, "Un seul '/' autorisé");
        }
        Ipv6Address address = Ipv6Address.parseStrict(text.substring(0, slash), field);
        String prefixText = text.substring(slash + 1);
        if (!prefixText.matches("[0-9]{1,3}")) {
            throw InvalidInputException.of(INVALID_PREFIX, field, "Le préfixe doit être un nombre entier positif");
        }
        int prefix = Integer.parseInt(prefixText);
        if (prefix > 128) {
            throw InvalidInputException.of(PREFIX_OUT_OF_RANGE, field, "Le préfixe doit être compris entre 0 et 128");
        }
        return new Ipv6Cidr(address, prefix);
    }

    public Ipv6Address mask() {
        return Ipv6Address.maskOf(prefix);
    }

    public Ipv6Address networkAddress() {
        return address.and(mask());
    }

    /** Il n'y a pas de broadcast en IPv6 : c'est simplement la dernière adresse du bloc. */
    public Ipv6Address lastAddress() {
        return networkAddress().or(mask().not());
    }

    public boolean isNetworkAddress() {
        return address.equals(networkAddress());
    }

    public Ipv6Cidr toNetwork() {
        return new Ipv6Cidr(networkAddress(), prefix);
    }

    /** Nombre d'adresses du bloc : 2^(128 - n), jusqu'à 2^128 pour un /0, d'où le BigInteger. */
    public BigInteger size() {
        return BigInteger.ONE.shiftLeft(128 - prefix);
    }

    /** Vrai si tout le bloc other est inclus dans ce bloc. */
    public boolean contains(Ipv6Cidr other) {
        return other.prefix >= prefix && other.address.and(mask()).equals(networkAddress());
    }

    public Optional<String> correctionWarning() {
        return isNetworkAddress()
                ? Optional.empty()
                : Optional.of("Adresse corrigée vers le réseau " + toNetwork());
    }

    @Override
    public String toString() {
        return address + "/" + prefix;
    }
}
