package com.coco.subnet.dto;

/**
 * Façon de saisir les VLAN sur la page web.
 * EQUAL_SPLIT : nombre de VLAN seul, réseau découpé en parts égales.
 * UNIFORM : nombre de VLAN + hôtes communs. CUSTOM : hôtes saisis ligne par ligne.
 */
public enum VlanInputMode {
    EQUAL_SPLIT, UNIFORM, CUSTOM
}
