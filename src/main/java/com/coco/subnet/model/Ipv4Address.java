package com.coco.subnet.model;

import static com.coco.subnet.model.ErrorCode.EMPTY_INPUT;
import static com.coco.subnet.model.ErrorCode.EMPTY_OCTET;
import static com.coco.subnet.model.ErrorCode.INVALID_CHARACTERS;
import static com.coco.subnet.model.ErrorCode.LEADING_ZERO;
import static com.coco.subnet.model.ErrorCode.OCTET_OUT_OF_RANGE;
import static com.coco.subnet.model.ErrorCode.WRONG_OCTET_COUNT;

/** Une adresse IPv4 stockée dans un int de 32 bits (signé : 128.0.0.0 et plus sont négatifs). */
public record Ipv4Address(int value) {

    public static Ipv4Address parse(String input, String field) {
        if (input == null || input.isBlank()) {
            throw InvalidInputException.of(EMPTY_INPUT, field, "L'adresse est obligatoire");
        }
        return parseStrict(input.strip(), field);
    }

    /** Sans retirer les espaces : utilisé par Cidr, qui a déjà nettoyé la saisie complète. */
    static Ipv4Address parseStrict(String text, String field) {
        if (text.isEmpty()) {
            throw InvalidInputException.of(EMPTY_INPUT, field, "L'adresse est obligatoire");
        }
        for (char c : text.toCharArray()) {
            if ((c < '0' || c > '9') && c != '.') {
                throw InvalidInputException.of(INVALID_CHARACTERS, field, "Caractère non autorisé : '" + c + "'");
            }
        }
        // -1 : garder les morceaux vides en fin de chaîne ("1.2.3.4." → 5 morceaux)
        String[] parts = text.split("\\.", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                throw InvalidInputException.of(EMPTY_OCTET, field, "Octet vide en position " + (i + 1));
            }
        }
        if (parts.length != 4) {
            throw InvalidInputException.of(WRONG_OCTET_COUNT, field,
                    "Une adresse IPv4 contient 4 octets (" + parts.length + " trouvés)");
        }
        for (String part : parts) {
            if (part.length() > 1 && part.charAt(0) == '0') {
                throw InvalidInputException.of(LEADING_ZERO, field,
                        "Zéro initial interdit dans '" + part + "' (ambigu : octal)");
            }
        }
        int value = 0;
        for (String part : parts) {
            // plus de 3 chiffres : hors limites, sans risquer un dépassement dans parseInt
            if (part.length() > 3 || Integer.parseInt(part) > 255) {
                throw InvalidInputException.of(OCTET_OUT_OF_RANGE, field,
                        "L'octet " + part + " est hors limites (0–255)");
            }
            value = (value << 8) | Integer.parseInt(part);
        }
        return new Ipv4Address(value);
    }

    @Override
    public String toString() {
        return ((value >>> 24) & 0xFF) + "." + ((value >>> 16) & 0xFF) + "."
                + ((value >>> 8) & 0xFF) + "." + (value & 0xFF);
    }
}
