package com.coco.subnet.model;

import java.util.Objects;
import java.util.Optional;

import static com.coco.subnet.model.ErrorCode.EMPTY_INPUT;
import static com.coco.subnet.model.ErrorCode.INVALID_PREFIX;
import static com.coco.subnet.model.ErrorCode.MISSING_PREFIX;
import static com.coco.subnet.model.ErrorCode.MULTIPLE_SLASHES;
import static com.coco.subnet.model.ErrorCode.PREFIX_OUT_OF_RANGE;

/** Une adresse et un préfixe, par exemple 192.168.10.0/24. */
public record Cidr(Ipv4Address address, int prefix) {

    public Cidr {
        Objects.requireNonNull(address, "address");
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Préfixe hors limites : " + prefix);
        }
    }

    /** Ordre des vérifications : vide → nombre de '/' → adresse → préfixe. */
    public static Cidr parse(String input, String field) {
        if (input == null || input.isBlank()) {
            throw InvalidInputException.of(EMPTY_INPUT, field, "L'adresse est obligatoire");
        }
        String text = input.strip();
        int slash = text.indexOf('/');
        if (slash < 0) {
            throw InvalidInputException.of(MISSING_PREFIX, field, "Préfixe manquant : format attendu a.b.c.d/n");
        }
        if (text.indexOf('/', slash + 1) >= 0) {
            throw InvalidInputException.of(MULTIPLE_SLASHES, field, "Un seul '/' autorisé");
        }
        // parseStrict : un espace avant le '/' ("1.2.3.4 /24") reste interdit
        Ipv4Address address = Ipv4Address.parseStrict(text.substring(0, slash), field);
        String prefixText = text.substring(slash + 1);
        if (!prefixText.matches("[0-9]{1,3}")) {
            throw InvalidInputException.of(INVALID_PREFIX, field, "Le préfixe doit être un nombre entier positif");
        }
        int prefix = Integer.parseInt(prefixText);
        if (prefix > 32) {
            throw InvalidInputException.of(PREFIX_OUT_OF_RANGE, field, "Le préfixe doit être compris entre 0 et 32");
        }
        return new Cidr(address, prefix);
    }

    public Ipv4Address mask() {
        return Ipv4Address.maskOf(prefix);
    }

    public Ipv4Address networkAddress() {
        return address.and(mask());
    }

    public boolean isNetworkAddress() {
        return address.equals(networkAddress());
    }

    public Cidr toNetwork() {
        return new Cidr(networkAddress(), prefix);
    }

    /** Nombre total d'adresses du bloc (un /0 en contient 2^32, d'où le long). */
    public long size() {
        return 1L << (32 - prefix);
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
