package com.coco.subnet.dto;

import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;

import java.util.ArrayList;
import java.util.List;

/** Formulaire de la page VLSM/VLAN. Pas d'annotations : c'est le modèle qui valide (mêmes messages partout). */
public class VlsmForm {

    private String network = "";
    private boolean useVlans = true;
    private VlanInputMode vlanMode = VlanInputMode.CUSTOM;
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

    /**
     * Entrée dans « Nombre de VLAN » envoie « Calculer » (bouton par défaut) :
     * les lignes ne correspondent alors plus au nombre saisi et doivent être générées d'abord.
     */
    public boolean rowsOutOfDate() {
        return useVlans && vlanMode != VlanInputMode.CUSTOM && vlanCount != null && vlanCount != rows.size();
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

    public VlanInputMode getVlanMode() {
        return vlanMode;
    }

    public void setVlanMode(VlanInputMode vlanMode) {
        this.vlanMode = vlanMode == null ? VlanInputMode.CUSTOM : vlanMode;
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
