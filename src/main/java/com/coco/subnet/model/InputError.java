package com.coco.subnet.model;

/** Une erreur de saisie : quel code, sur quel champ, avec quel message pour l'utilisateur. */
public record InputError(ErrorCode code, String field, String message) {
}
