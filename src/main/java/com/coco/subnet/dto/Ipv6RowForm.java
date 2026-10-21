package com.coco.subnet.dto;

/** Une ligne du tableau du plan IPv6 dans le formulaire web : pas de nombre d'hôtes (tous les sous-réseaux ont la même taille). */
public class Ipv6RowForm {

    private Integer vlanId;
    private String name;

    public Ipv6RowForm() {
    }

    public Ipv6RowForm(Integer vlanId, String name) {
        this.vlanId = vlanId;
        this.name = name;
    }

    public Integer getVlanId() {
        return vlanId;
    }

    public void setVlanId(Integer vlanId) {
        this.vlanId = vlanId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
