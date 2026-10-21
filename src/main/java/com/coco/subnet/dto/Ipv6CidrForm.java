package com.coco.subnet.dto;

/** Formulaire de la page IPv6 (JavaBean : Spring le remplit avec les champs postés). */
public class Ipv6CidrForm {

    private String cidr = "";

    public String getCidr() {
        return cidr;
    }

    public void setCidr(String cidr) {
        this.cidr = cidr;
    }
}
