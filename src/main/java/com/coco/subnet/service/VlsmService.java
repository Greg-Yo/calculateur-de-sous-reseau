package com.coco.subnet.service;

import com.coco.subnet.model.AllocatedSubnet;
import com.coco.subnet.model.Cidr;
import com.coco.subnet.model.Ipv4Address;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.SubnetInfo;
import com.coco.subnet.model.VlsmPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class VlsmService {

    public VlsmPlan allocate(String networkText, PlanMode mode, List<Requirement> requirements) {
        Cidr parent = Cidr.parse(networkText, "network");
        return allocateValidated(parent, mode, requirements);
    }

    /** Alloue les blocs du plus grand au plus petit, à la suite, à partir du début du réseau parent. */
    private VlsmPlan allocateValidated(Cidr parent, PlanMode mode, List<Requirement> requirements) {
        Cidr network = parent.toNetwork();
        List<Integer> order = new ArrayList<>(IntStream.range(0, requirements.size()).boxed().toList());
        // List.sort est stable : à taille égale, l'ordre de saisie est conservé
        order.sort(Comparator.comparingLong((Integer i) -> blockSize(mode, requirements.get(i).hosts())).reversed());

        long start = network.address().toUnsignedLong();
        long cursor = start;
        List<AllocatedSubnet> subnets = new ArrayList<>();
        for (int i : order) {
            Requirement requirement = requirements.get(i);
            long block = blockSize(mode, requirement.hosts());
            SubnetInfo subnet = SubnetInfo.of(new Cidr(new Ipv4Address((int) cursor), prefixOf(block)));
            Ipv4Address gateway = mode == PlanMode.VLAN ? subnet.firstUsable() : null;
            long free = subnet.usableHosts() - requirement.hosts() - (mode == PlanMode.VLAN ? 1 : 0);
            subnets.add(new AllocatedSubnet(requirement, subnet, gateway, free));
            cursor += block;
        }
        long used = cursor - start;
        return new VlsmPlan(network, mode, List.copyOf(subnets), used, network.size() - used,
                parent.correctionWarning().stream().toList());
    }

    /** Plus petite puissance de 2 qui contient les hôtes + réseau + broadcast (+ passerelle en VLAN). */
    static long blockSize(PlanMode mode, long hosts) {
        long needed = hosts + 2 + (mode == PlanMode.VLAN ? 1 : 0);
        long highest = Long.highestOneBit(needed);
        return highest == needed ? needed : highest << 1;
    }

    static int prefixOf(long blockSize) {
        return 32 - Long.numberOfTrailingZeros(blockSize);
    }
}
