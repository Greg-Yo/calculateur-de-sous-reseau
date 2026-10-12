package com.coco.subnet.controller;

import java.io.IOException;

/** Levée pendant la lecture d'un corps de requête qui dépasse la limite autorisée. */
public class PayloadTooLargeException extends IOException {

    public PayloadTooLargeException(long maxBytes) {
        super("Corps de requête supérieur à " + maxBytes + " octets");
    }
}
