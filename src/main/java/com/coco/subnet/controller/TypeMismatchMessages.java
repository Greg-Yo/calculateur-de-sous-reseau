package com.coco.subnet.controller;

import org.springframework.beans.PropertyAccessException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DefaultBindingErrorProcessor;
import org.springframework.validation.FieldError;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Texte saisi dans un champ numérique des formulaires web : le message dit quel champ corriger,
 * et sur quelle ligne. Sans cela, le récapitulatif affiche « Doit être un nombre entier » sans contexte.
 * Le code reste « typeMismatch ».
 */
final class TypeMismatchMessages extends DefaultBindingErrorProcessor {

    private static final Pattern ROW_FIELD = Pattern.compile("rows\\[(\\d+)]\\.(\\w+)");

    @Override
    public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult) {
        if (!(ex instanceof TypeMismatchException)) {
            super.processPropertyAccessException(ex, bindingResult);
            return;
        }
        String field = ex.getPropertyName();
        String[] codes = bindingResult.resolveMessageCodes(ex.getErrorCode(), field);
        bindingResult.addError(new FieldError(bindingResult.getObjectName(), field, ex.getValue(), true,
                codes, null, messageFor(field)));
    }

    static String messageFor(String field) {
        Matcher row = ROW_FIELD.matcher(field == null ? "" : field);
        if (row.matches()) {
            String line = "Ligne " + (Integer.parseInt(row.group(1)) + 1) + " : ";
            return switch (row.group(2)) {
                case "vlanId" -> line + "l'ID du VLAN doit être un nombre entier";
                case "hosts" -> line + "le nombre d'hôtes doit être un nombre entier";
                default -> line + "doit être un nombre entier";
            };
        }
        return switch (field == null ? "" : field) {
            case "vlanCount" -> "Le nombre de VLAN doit être un nombre entier";
            case "hostsPerVlan" -> "Le nombre d'hôtes par VLAN doit être un nombre entier";
            case "subnetPrefix" -> "La taille des sous-réseaux doit être un nombre entier";
            default -> "Doit être un nombre entier";
        };
    }
}
