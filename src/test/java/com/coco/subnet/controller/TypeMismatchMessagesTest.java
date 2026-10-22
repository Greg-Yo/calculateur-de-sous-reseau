package com.coco.subnet.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TypeMismatchMessagesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "rows[0].vlanId | Ligne 1 : l'ID du VLAN doit être un nombre entier",
            "rows[11].hosts | Ligne 12 : le nombre d'hôtes doit être un nombre entier",
            "rows[2].other  | Ligne 3 : doit être un nombre entier",
            "vlanCount      | Le nombre de VLAN doit être un nombre entier",
            "hostsPerVlan   | Le nombre d'hôtes par VLAN doit être un nombre entier",
            "subnetPrefix   | La taille des sous-réseaux doit être un nombre entier",
            "unknown        | Doit être un nombre entier"
    })
    void namesTheFieldAndTheLine(String field, String message) {
        assertThat(TypeMismatchMessages.messageFor(field)).isEqualTo(message);
    }
}
