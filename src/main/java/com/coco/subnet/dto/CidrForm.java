package com.coco.subnet.dto;

/** Formulaire de la page CIDR (JavaBean : Spring le remplit avec les champs postés). */
public class CidrForm {

    private String cidr = "";

    public String getCidr() {
        return cidr;
    }

    public void setCidr(String cidr) {
        this.cidr = cidr;
    }
}
