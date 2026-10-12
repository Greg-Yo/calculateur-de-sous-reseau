package com.coco.subnet.dto;

import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;

import java.util.ArrayList;
import java.util.List;

/** Formulaire de la page VLSM/VLAN. Pas d'annotations : c'est le modèle qui valide (mêmes messages partout). */
public class VlsmForm {

    private String network = "";
    private boolean useVlans = true;
    private Integer vlanCount;
    private Long hostsPerVlan;
    private List<RowForm> rows = new ArrayList<>();

    public PlanMode mode() {
        return useVlans ? PlanMode.VLAN : PlanMode.REQUIREMENTS;
    }

    public List<Requirement> toRequirements() {
        return rows.stream()
                .map(row -> new Requirement(useVlans ? row.getVlanId() : null, row.getName(), row.getHosts()))
                .toList();
    }

    public List<Integer> vlanIds() {
        return rows.stream().map(RowForm::getVlanId).toList();
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public boolean isUseVlans() {
        return useVlans;
    }

    public void setUseVlans(boolean useVlans) {
        this.useVlans = useVlans;
    }

    public Integer getVlanCount() {
        return vlanCount;
    }

    public void setVlanCount(Integer vlanCount) {
        this.vlanCount = vlanCount;
    }

    public Long getHostsPerVlan() {
        return hostsPerVlan;
    }

    public void setHostsPerVlan(Long hostsPerVlan) {
        this.hostsPerVlan = hostsPerVlan;
    }

    public List<RowForm> getRows() {
        return rows;
    }

    public void setRows(List<RowForm> rows) {
        this.rows = rows;
    }
}
