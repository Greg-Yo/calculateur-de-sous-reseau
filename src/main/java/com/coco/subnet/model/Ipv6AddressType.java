package com.coco.subnet.model;

/** Grandes familles d'adresses IPv6, testées dans l'ordre de déclaration. */
public enum Ipv6AddressType {
    UNSPECIFIED("::/128", "Adresse non spécifiée"),
    LOOPBACK("::1/128", "Boucle locale"),
    IPV4_MAPPED("::ffff:0:0/96", "IPv4 mappée"),
    // Avant GLOBAL_UNICAST : 2001:db8::/32 est inclus dans 2000::/3
    DOCUMENTATION("2001:db8::/32", "Documentation (exemples)"),
    GLOBAL_UNICAST("2000::/3", "Globale (routable sur Internet)"),
    UNIQUE_LOCAL("fc00::/7", "Unique locale (privée)"),
    LINK_LOCAL("fe80::/10", "Lien local"),
    MULTICAST("ff00::/8", "Multicast"),
    OTHER(null, "Autre / réservée");

    private final Ipv6Cidr range;
    private final String label;

    Ipv6AddressType(String range, String label) {
        this.range = range == null ? null : Ipv6Cidr.parse(range, "range");
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Premier type dont la plage contient tout le bloc ; OTHER sinon. */
    public static Ipv6AddressType of(Ipv6Cidr cidr) {
        for (Ipv6AddressType type : values()) {
            if (type.range != null && type.range.contains(cidr)) {
                return type;
            }
        }
        return OTHER;
    }
}
