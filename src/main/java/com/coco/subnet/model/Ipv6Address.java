package com.coco.subnet.model;

import static com.coco.subnet.model.ErrorCode.EMPTY_GROUP;
import static com.coco.subnet.model.ErrorCode.EMPTY_INPUT;
import static com.coco.subnet.model.ErrorCode.GROUP_TOO_LONG;
import static com.coco.subnet.model.ErrorCode.INVALID_CHARACTERS;
import static com.coco.subnet.model.ErrorCode.MULTIPLE_DOUBLE_COLON;
import static com.coco.subnet.model.ErrorCode.WRONG_GROUP_COUNT;

/**
 * Une adresse IPv6 : 128 bits rangés dans deux long.
 * high = les 4 premiers groupes, low = les 4 derniers (signés : 8000:: et plus ont un high négatif).
 */
public record Ipv6Address(long high, long low) {

    public static Ipv6Address parse(String input, String field) {
        if (input == null || input.isBlank()) {
            throw InvalidInputException.of(EMPTY_INPUT, field, "L'adresse est obligatoire");
        }
        return parseStrict(input.strip(), field);
    }

    /**
     * Sans retirer les espaces : utilisé par Ipv6Cidr, qui a déjà nettoyé la saisie complète.
     * Ordre des vérifications (spec §6.1) : vide → caractères → nombre de "::" → groupe vide
     * → nombre de groupes → longueur d'un groupe.
     */
    static Ipv6Address parseStrict(String text, String field) {
        if (text.isEmpty()) {
            throw InvalidInputException.of(EMPTY_INPUT, field, "L'adresse est obligatoire");
        }
        for (char c : text.toCharArray()) {
            if (!isHexDigit(c) && c != ':') {
                throw InvalidInputException.of(INVALID_CHARACTERS, field,
                        "Caractère non autorisé : '" + c + "'" + hintFor(c));
            }
        }
        int doubleColon = text.indexOf("::");
        // + 1 et non + 2 : dans "1:::2", le second "::" commence juste après le premier ':'
        if (doubleColon >= 0 && text.indexOf("::", doubleColon + 1) >= 0) {
            throw InvalidInputException.of(MULTIPLE_DOUBLE_COLON, field, "« :: » ne peut apparaître qu'une seule fois");
        }
        String[] left = groups(doubleColon < 0 ? text : text.substring(0, doubleColon), field);
        String[] right = doubleColon < 0 ? new String[0] : groups(text.substring(doubleColon + 2), field);
        int written = left.length + right.length;
        if (doubleColon < 0 && written != 8) {
            throw InvalidInputException.of(WRONG_GROUP_COUNT, field,
                    "Une adresse IPv6 contient 8 groupes (" + written + " trouvés)");
        }
        if (doubleColon >= 0 && written > 7) {
            throw InvalidInputException.of(WRONG_GROUP_COUNT, field,
                    "Avec « :: », au plus 7 groupes peuvent être écrits (" + written + " trouvés)");
        }
        // Les groupes absents (remplacés par "::") restent à 0
        int[] values = new int[8];
        for (int i = 0; i < left.length; i++) {
            values[i] = groupValue(left[i], field);
        }
        for (int i = 0; i < right.length; i++) {
            values[8 - right.length + i] = groupValue(right[i], field);
        }
        long high = 0;
        long low = 0;
        for (int i = 0; i < 4; i++) {
            high = (high << 16) | values[i];
            low = (low << 16) | values[i + 4];
        }
        return new Ipv6Address(high, low);
    }

    /** Morceaux entre les ':'. Une partie vide (à gauche de "::1") ne contient aucun groupe. */
    private static String[] groups(String part, String field) {
        if (part.isEmpty()) {
            return new String[0];
        }
        // -1 : garder les morceaux vides en fin de chaîne ("1:2:" → 3 morceaux)
        String[] groups = part.split(":", -1);
        for (String group : groups) {
            if (group.isEmpty()) {
                throw InvalidInputException.of(EMPTY_GROUP, field,
                        "Groupe vide : un ':' seul est interdit en début ou en fin d'adresse");
            }
        }
        return groups;
    }

    /** Valeur d'un groupe (0 à 0xFFFF). Plus de 4 chiffres : GROUP_TOO_LONG, sans appeler parseInt. */
    private static int groupValue(String group, String field) {
        if (group.length() > 4) {
            throw InvalidInputException.of(GROUP_TOO_LONG, field,
                    "Le groupe '" + group + "' dépasse 4 chiffres hexadécimaux");
        }
        return Integer.parseInt(group, 16);
    }

    /** Pas de Character.digit : il accepterait les chiffres Unicode « pleine chasse ». */
    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /** Complément du message INVALID_CHARACTERS pour '.' et '%'. */
    private static String hintFor(char c) {
        return switch (c) {
            case '.' -> " (la notation IPv4 intégrée n'est pas prise en charge)";
            case '%' -> " (les identifiants de zone ne sont pas pris en charge)";
            default -> "";
        };
    }
}
