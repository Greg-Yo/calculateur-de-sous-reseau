package com.coco.subnet.dto;

/** Une ligne du tableau VLSM/VLAN dans le formulaire web. */
public class RowForm {

    private Integer vlanId;
    private String name;
    private Long hosts;

    public RowForm() {
    }

    public RowForm(Integer vlanId, String name, Long hosts) {
        this.vlanId = vlanId;
        this.name = name;
        this.hosts = hosts;
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

    public Long getHosts() {
        return hosts;
    }

    public void setHosts(Long hosts) {
        this.hosts = hosts;
    }
}
