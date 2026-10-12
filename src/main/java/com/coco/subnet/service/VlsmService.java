package com.coco.subnet.service;

import com.coco.subnet.model.AllocatedSubnet;
import com.coco.subnet.model.Cidr;
import com.coco.subnet.model.InputError;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.model.Ipv4Address;
import com.coco.subnet.model.PlanMode;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.SubnetInfo;
import com.coco.subnet.model.VlsmPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.coco.subnet.model.ErrorCode.DUPLICATE_NAME;
import static com.coco.subnet.model.ErrorCode.DUPLICATE_VLAN_ID;
import static com.coco.subnet.model.ErrorCode.EMPTY_NAME;
import static com.coco.subnet.model.ErrorCode.HOSTS_NOT_POSITIVE;
import static com.coco.subnet.model.ErrorCode.HOSTS_TOO_LARGE;
import static com.coco.subnet.model.ErrorCode.INVALID_NAME;
import static com.coco.subnet.model.ErrorCode.MISSING_FIELD;
import static com.coco.subnet.model.ErrorCode.NETWORK_TOO_SMALL;
import static com.coco.subnet.model.ErrorCode.NO_REQUIREMENTS;
import static com.coco.subnet.model.ErrorCode.PARENT_NOT_SPLITTABLE;
import static com.coco.subnet.model.ErrorCode.REQUIREMENT_TOO_LARGE;
import static com.coco.subnet.model.ErrorCode.TOO_MANY_REQUIREMENTS;
import static com.coco.subnet.model.ErrorCode.VLAN_COUNT_OUT_OF_RANGE;
import static com.coco.subnet.model.ErrorCode.VLAN_ID_OUT_OF_RANGE;

@Service
public class VlsmService {

    public static final int MAX_REQUIREMENTS = 100;
    public static final int MAX_VLAN_COUNT = 100;
    public static final int MAX_VLAN_ID = 4094;
    /** Un bloc /0 : 2^32 adresses moins le réseau et le broadcast. */
    public static final long MAX_HOSTS = 4_294_967_294L;
    /** Commence par une lettre ou un chiffre : empêche l'injection de formules dans Excel. */
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,31}$");

    public VlsmPlan allocate(String networkText, PlanMode mode, List<Requirement> requirements) {
        List<InputError> errors = new ArrayList<>();
        Cidr parent = parseParent(networkText, errors);
        List<Requirement> resolved = validateRequirements(mode, requirements, errors);
        throwIfAny(errors);
        // Les vérifications globales n'ont de sens que si chaque champ est valide
        checkDuplicates(mode, resolved, errors);
        throwIfAny(errors);
        checkCapacity(mode, parent.toNetwork(), resolved, errors);
        throwIfAny(errors);
        return allocateValidated(parent, mode, resolved);
    }

    /** Raccourci « même nombre d'hôtes pour tous » : génère des lignes détaillées et modifiables. */
    public List<Requirement> generateUniformVlans(Integer vlanCount, Long hostsPerVlan) {
        List<InputError> errors = new ArrayList<>();
        if (vlanCount == null) {
            errors.add(new InputError(MISSING_FIELD, "vlanCount", "Le champ « Nombre de VLAN » est obligatoire"));
        } else if (vlanCount < 1 || vlanCount > MAX_VLAN_COUNT) {
            errors.add(new InputError(VLAN_COUNT_OUT_OF_RANGE, "vlanCount", "Le nombre de VLAN doit être entre 1 et 100"));
        }
        long max = maxHosts(PlanMode.VLAN);
        if (hostsPerVlan == null) {
            errors.add(new InputError(MISSING_FIELD, "hostsPerVlan", "Le champ « Hôtes par VLAN » est obligatoire"));
        } else if (hostsPerVlan < 1) {
            errors.add(new InputError(HOSTS_NOT_POSITIVE, "hostsPerVlan", "Au moins 1 hôte requis"));
        } else if (hostsPerVlan > max) {
            errors.add(new InputError(HOSTS_TOO_LARGE, "hostsPerVlan",
                    "Un sous-réseau IPv4 ne peut pas dépasser " + groupDigits(max) + " hôtes (hors passerelle)"));
        }
        throwIfAny(errors);
        return IntStream.rangeClosed(1, vlanCount)
                .mapToObj(i -> new Requirement(i * 10, "VLAN" + (i * 10), hostsPerVlan))
                .toList();
    }

    /**
     * ID proposé pour une nouvelle ligne : plus grand ID existant + 10, ou 10.
     * Au-delà de 4094, premier multiple de 10 encore libre (il en reste toujours : 100 lignes au plus).
     */
    public int nextVlanId(Collection<Integer> existingIds) {
        Set<Integer> used = existingIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        int next = used.stream().mapToInt(Integer::intValue).max().orElse(0) + 10;
        if (next <= MAX_VLAN_ID) {
            return next;
        }
        return IntStream.iterate(10, id -> id + 10).filter(id -> !used.contains(id)).findFirst().getAsInt();
    }

    private Cidr parseParent(String networkText, List<InputError> errors) {
        try {
            Cidr parent = Cidr.parse(networkText, "network");
            if (parent.prefix() >= 31) {
                errors.add(new InputError(PARENT_NOT_SPLITTABLE, "network", "Un /31 ou /32 ne peut pas être découpé"));
            }
            return parent;
        } catch (InvalidInputException e) {
            errors.addAll(e.errors());
            return null;
        }
    }

    private List<Requirement> validateRequirements(PlanMode mode, List<Requirement> requirements, List<InputError> errors) {
        String list = listField(mode);
        if (requirements == null || requirements.isEmpty()) {
            errors.add(new InputError(NO_REQUIREMENTS, list, "Ajoutez au moins un VLAN ou un besoin"));
            return List.of();
        }
        if (requirements.size() > MAX_REQUIREMENTS) {
            errors.add(new InputError(TOO_MANY_REQUIREMENTS, list, "Maximum 100 sous-réseaux"));
            return List.of();
        }
        List<Requirement> resolved = new ArrayList<>();
        for (int i = 0; i < requirements.size(); i++) {
            Requirement row = requirements.get(i) == null ? new Requirement(null, null, null) : requirements.get(i);
            resolved.add(validateRow(mode, i, row, errors));
        }
        return resolved;
    }

    /** Valide une ligne et renvoie le besoin « résolu » (nom nettoyé, nom par défaut en VLAN). */
    private Requirement validateRow(PlanMode mode, int index, Requirement row, List<InputError> errors) {
        String path = listField(mode) + "[" + index + "].";
        String name = row.name() == null ? "" : row.name().strip();
        Integer vlanId = null;
        String label;
        if (mode == PlanMode.VLAN) {
            vlanId = row.vlanId();
            if (vlanId == null) {
                errors.add(new InputError(MISSING_FIELD, path + "id", "Ligne " + (index + 1) + " : l'ID du VLAN est obligatoire"));
                label = "Ligne " + (index + 1);
            } else if (vlanId < 1 || vlanId > MAX_VLAN_ID) {
                errors.add(new InputError(VLAN_ID_OUT_OF_RANGE, path + "id", "VLAN " + vlanId + " : l'ID doit être entre 1 et 4094"));
                label = "Ligne " + (index + 1);
            } else {
                label = "VLAN " + vlanId;
            }
            if (name.isEmpty() && vlanId != null) {
                name = "VLAN" + vlanId;
            }
        } else {
            label = NAME.matcher(name).matches() ? name : "Besoin n°" + (index + 1);
            if (name.isEmpty()) {
                errors.add(new InputError(EMPTY_NAME, path + "name", "Le besoin n°" + (index + 1) + " doit avoir un nom"));
            }
        }
        if (!name.isEmpty() && !NAME.matcher(name).matches()) {
            errors.add(new InputError(INVALID_NAME, path + "name",
                    label + " : nom invalide : lettres (accents compris), chiffres, - et _ (32 max)"));
        }
        Long hosts = row.hosts();
        long max = maxHosts(mode);
        if (hosts == null) {
            errors.add(new InputError(MISSING_FIELD, path + "hosts", label + " : le nombre d'hôtes est obligatoire"));
        } else if (hosts < 1) {
            errors.add(new InputError(HOSTS_NOT_POSITIVE, path + "hosts", label + " : au moins 1 hôte requis"));
        } else if (hosts > max) {
            errors.add(new InputError(HOSTS_TOO_LARGE, path + "hosts", label + " : un sous-réseau IPv4 ne peut pas dépasser "
                    + groupDigits(max) + " hôtes" + (mode == PlanMode.VLAN ? " (hors passerelle)" : "")));
        }
        return new Requirement(vlanId, name, hosts);
    }

    private void checkDuplicates(PlanMode mode, List<Requirement> requirements, List<InputError> errors) {
        String list = listField(mode);
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < requirements.size(); i++) {
            Requirement r = requirements.get(i);
            if (mode == PlanMode.VLAN && !ids.add(r.vlanId())) {
                errors.add(new InputError(DUPLICATE_VLAN_ID, list + "[" + i + "].id", "VLAN " + r.vlanId() + " est en double"));
                continue; // même ID → même nom par défaut : une seule erreur suffit
            }
            if (!names.add(r.name().toLowerCase(Locale.ROOT))) {
                errors.add(new InputError(DUPLICATE_NAME, list + "[" + i + "].name", "Le nom '" + r.name() + "' est utilisé deux fois"));
            }
        }
    }

    private void checkCapacity(PlanMode mode, Cidr network, List<Requirement> requirements, List<InputError> errors) {
        long capacity = network.size();
        long total = 0;
        for (int i = 0; i < requirements.size(); i++) {
            Requirement r = requirements.get(i);
            long block = blockSize(mode, r.hosts());
            total += block;
            if (block > capacity) {
                errors.add(new InputError(REQUIREMENT_TOO_LARGE, listField(mode) + "[" + i + "].hosts",
                        label(mode, r) + " (" + r.hosts() + " hôtes, bloc /" + prefixOf(block)
                                + ") ne tient pas dans un /" + network.prefix()));
            }
        }
        if (errors.isEmpty() && total > capacity) {
            errors.add(new InputError(NETWORK_TOO_SMALL, "network", "Le /" + network.prefix() + " (" + capacity
                    + " adresses) ne suffit pas : " + total + " nécessaires, il manque " + (total - capacity)));
        }
    }

    /** Libellé d'une ligne déjà validée, utilisé en tête des messages. */
    private static String label(PlanMode mode, Requirement r) {
        return mode == PlanMode.VLAN ? "VLAN " + r.vlanId() : r.name();
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

    static long maxHosts(PlanMode mode) {
        return mode == PlanMode.VLAN ? MAX_HOSTS - 1 : MAX_HOSTS;
    }

    private static String listField(PlanMode mode) {
        return mode == PlanMode.VLAN ? "vlans" : "requirements";
    }

    /** 4294967294 → "4 294 967 294". */
    private static String groupDigits(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    private static void throwIfAny(List<InputError> errors) {
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }
    }
}
