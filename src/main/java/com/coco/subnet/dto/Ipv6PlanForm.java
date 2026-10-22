package com.coco.subnet.dto;

import com.coco.subnet.model.Ipv6Requirement;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.service.Ipv6PlanService;

import java.util.ArrayList;
import java.util.List;

/** Formulaire de la page « Plan IPv6 ». Pas d'annotations : c'est le modèle qui valide (mêmes messages partout). */
public class Ipv6PlanForm {

    private String network = "";
    /** Prérempli avec 64 ; vidé par l'utilisateur, il vaut null et le modèle renvoie MISSING_FIELD. */
    private Integer subnetPrefix = Ipv6PlanService.DEFAULT_SUBNET_PREFIX;
    private boolean useVlans = true;
    private Integer vlanCount;
    private List<Ipv6RowForm> rows = new ArrayList<>();

    public PlanMode mode() {
        return useVlans ? PlanMode.VLAN : PlanMode.REQUIREMENTS;
    }

    public List<Ipv6Requirement> toRequirements() {
        return rows.stream()
                .map(row -> new Ipv6Requirement(useVlans ? row.getVlanId() : null, row.getName()))
                .toList();
    }

    public List<Integer> vlanIds() {
        return rows.stream().map(Ipv6RowForm::getVlanId).toList();
    }

    /**
     * Entrée dans « Nombre de VLAN » envoie « Calculer » (bouton par défaut) :
     * les lignes ne correspondent alors plus au nombre saisi et doivent être générées d'abord.
     */
    public boolean rowsOutOfDate() {
        return useVlans && vlanCount != null && vlanCount != rows.size();
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public Integer getSubnetPrefix() {
        return subnetPrefix;
    }

    public void setSubnetPrefix(Integer subnetPrefix) {
        this.subnetPrefix = subnetPrefix;
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

    public List<Ipv6RowForm> getRows() {
        return rows;
    }

    public void setRows(List<Ipv6RowForm> rows) {
        this.rows = rows;
    }
}
