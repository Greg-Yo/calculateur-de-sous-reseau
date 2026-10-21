package com.coco.subnet.service;

import com.coco.subnet.model.InputError;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.model.Ipv6Address;
import com.coco.subnet.model.Ipv6AllocatedSubnet;
import com.coco.subnet.model.Ipv6Cidr;
import com.coco.subnet.model.Ipv6Plan;
import com.coco.subnet.model.Ipv6Requirement;
import com.coco.subnet.model.PlanMode;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.coco.subnet.model.ErrorCode.DUPLICATE_NAME;
import static com.coco.subnet.model.ErrorCode.DUPLICATE_VLAN_ID;
import static com.coco.subnet.model.ErrorCode.EMPTY_NAME;
import static com.coco.subnet.model.ErrorCode.INVALID_NAME;
import static com.coco.subnet.model.ErrorCode.MISSING_FIELD;
import static com.coco.subnet.model.ErrorCode.NETWORK_TOO_SMALL;
import static com.coco.subnet.model.ErrorCode.NO_REQUIREMENTS;
import static com.coco.subnet.model.ErrorCode.PARENT_NOT_SPLITTABLE;
import static com.coco.subnet.model.ErrorCode.SUBNET_PREFIX_OUT_OF_RANGE;
import static com.coco.subnet.model.ErrorCode.TOO_MANY_REQUIREMENTS;
import static com.coco.subnet.model.ErrorCode.VLAN_COUNT_OUT_OF_RANGE;
import static com.coco.subnet.model.ErrorCode.VLAN_ID_OUT_OF_RANGE;

/** Découpe un préfixe IPv6 en sous-réseaux de même taille, un par VLAN ou par besoin nommé. */
@Service
public class Ipv6PlanService {

    public static final int DEFAULT_SUBNET_PREFIX = 64;
    private static final int MAX_SUBNET_PREFIX = 127;
    /** Même règle que la v1 : empêche aussi l'injection de formules dans Excel. */
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,31}$");

    public Ipv6Plan allocate(String networkText, Integer subnetPrefix, PlanMode mode, List<Ipv6Requirement> requirements) {
        List<InputError> errors = new ArrayList<>();
        Ipv6Cidr parent = parseParent(networkText, errors);
        validateSubnetPrefix(errors.isEmpty() ? parent : null, subnetPrefix, errors);
        List<Ipv6Requirement> resolved = validateRequirements(mode, requirements, errors);
        throwIfAny(errors);
        // Les vérifications globales n'ont de sens que si chaque champ est valide
        checkDuplicates(mode, resolved, errors);
        throwIfAny(errors);
        Ipv6Cidr network = parent.toNetwork();
        BigInteger available = BigInteger.ONE.shiftLeft(subnetPrefix - network.prefix());
        if (available.compareTo(BigInteger.valueOf(resolved.size())) < 0) {
            throw InvalidInputException.of(NETWORK_TOO_SMALL, "network", "Le /" + network.prefix() + " contient "
                    + available + " sous-réseaux /" + subnetPrefix + " : " + resolved.size() + " demandés, il en manque "
                    + (resolved.size() - available.intValue()));
        }
        List<Ipv6AllocatedSubnet> subnets = new ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            // Début du n° i : réseau + i × 2^(128 - préfixe), soit i décalé de (128 - préfixe) bits
            Ipv6Address start = network.address().plus(new Ipv6Address(0, i).shiftLeft(128 - subnetPrefix));
            Ipv6Address gateway = mode == PlanMode.VLAN ? start.plus(Ipv6Address.ONE) : null;
            subnets.add(new Ipv6AllocatedSubnet(resolved.get(i), new Ipv6Cidr(start, subnetPrefix), gateway));
        }
        List<String> warnings = new ArrayList<>(parent.correctionWarning().stream().toList());
        if (subnetPrefix > DEFAULT_SUBNET_PREFIX) {
            warnings.add("Sous-réseaux /" + subnetPrefix + " : plus longs que /64, l'autoconfiguration SLAAC ne fonctionnera pas");
        } else if (subnetPrefix < DEFAULT_SUBNET_PREFIX) {
            warnings.add("Sous-réseaux /" + subnetPrefix + " : plus courts que /64 ; un réseau local utilise normalement un /64");
        }
        return new Ipv6Plan(network, subnetPrefix, mode, List.copyOf(subnets), available, List.copyOf(warnings));
    }

    /** Raccourci : VLAN10, VLAN20… modifiables ensuite. */
    public List<Ipv6Requirement> generateVlans(Integer vlanCount) {
        if (vlanCount == null) {
            throw InvalidInputException.of(MISSING_FIELD, "vlanCount", "Le champ « Nombre de VLAN » est obligatoire");
        }
        if (vlanCount < 1 || vlanCount > VlsmService.MAX_VLAN_COUNT) {
            throw InvalidInputException.of(VLAN_COUNT_OUT_OF_RANGE, "vlanCount", "Le nombre de VLAN doit être entre 1 et 100");
        }
        return IntStream.rangeClosed(1, vlanCount)
                .mapToObj(i -> new Ipv6Requirement(i * 10, "VLAN" + (i * 10)))
                .toList();
    }

    private static Ipv6Cidr parseParent(String networkText, List<InputError> errors) {
        try {
            Ipv6Cidr parent = Ipv6Cidr.parse(networkText, "network");
            if (parent.prefix() >= MAX_SUBNET_PREFIX) {
                errors.add(new InputError(PARENT_NOT_SPLITTABLE, "network", "Un /127 ou /128 ne peut pas être découpé"));
            }
            return parent;
        } catch (InvalidInputException e) {
            errors.addAll(e.errors());
            return null;
        }
    }

    /** parent est null s'il est invalide ou non découpable : on vérifie alors seulement 1 à 127. */
    private static void validateSubnetPrefix(Ipv6Cidr parent, Integer subnetPrefix, List<InputError> errors) {
        if (subnetPrefix == null) {
            errors.add(new InputError(MISSING_FIELD, "subnetPrefix", "Le champ « Taille des sous-réseaux » est obligatoire"));
            return;
        }
        int min = parent == null ? 1 : parent.prefix() + 1;
        if (subnetPrefix < min || subnetPrefix > MAX_SUBNET_PREFIX) {
            errors.add(new InputError(SUBNET_PREFIX_OUT_OF_RANGE, "subnetPrefix",
                    "La taille des sous-réseaux doit être comprise entre /" + min + " et /" + MAX_SUBNET_PREFIX));
        }
    }

    private static List<Ipv6Requirement> validateRequirements(PlanMode mode, List<Ipv6Requirement> requirements,
                                                              List<InputError> errors) {
        String list = listField(mode);
        if (requirements == null || requirements.isEmpty()) {
            errors.add(new InputError(NO_REQUIREMENTS, list, "Ajoutez au moins un VLAN ou un besoin"));
            return List.of();
        }
        if (requirements.size() > VlsmService.MAX_REQUIREMENTS) {
            errors.add(new InputError(TOO_MANY_REQUIREMENTS, list, "Maximum 100 sous-réseaux"));
            return List.of();
        }
        List<Ipv6Requirement> resolved = new ArrayList<>();
        for (int i = 0; i < requirements.size(); i++) {
            Ipv6Requirement row = requirements.get(i) == null ? new Ipv6Requirement(null, null) : requirements.get(i);
            resolved.add(validateRow(mode, i, row, errors));
        }
        return resolved;
    }

    /** Valide une ligne et renvoie le besoin « résolu » (nom nettoyé, nom par défaut en VLAN). */
    private static Ipv6Requirement validateRow(PlanMode mode, int index, Ipv6Requirement row, List<InputError> errors) {
        String path = listField(mode) + "[" + index + "].";
        String name = row.name() == null ? "" : row.name().strip();
        Integer vlanId = null;
        String label;
        if (mode == PlanMode.VLAN) {
            vlanId = row.vlanId();
            if (vlanId == null) {
                errors.add(new InputError(MISSING_FIELD, path + "id", "Ligne " + (index + 1) + " : l'ID du VLAN est obligatoire"));
                label = "Ligne " + (index + 1);
            } else if (vlanId < 1 || vlanId > VlsmService.MAX_VLAN_ID) {
                errors.add(new InputError(VLAN_ID_OUT_OF_RANGE, path + "id", "VLAN " + vlanId + " : l'ID doit être entre 1 et 4094"));
                label = "Ligne " + (index + 1);
            } else {
                label = "VLAN " + vlanId;
            }
            if (name.isEmpty() && vlanId != null) {
                name = "VLAN" + vlanId;
            }
        } else {
            label = "Besoin n°" + (index + 1);
            if (name.isEmpty()) {
                errors.add(new InputError(EMPTY_NAME, path + "name", "Le besoin n°" + (index + 1) + " doit avoir un nom"));
            }
        }
        if (!name.isEmpty() && !NAME.matcher(name).matches()) {
            errors.add(new InputError(INVALID_NAME, path + "name",
                    label + " : nom invalide : lettres (accents compris), chiffres, - et _ (32 max)"));
        }
        return new Ipv6Requirement(vlanId, name);
    }

    private static void checkDuplicates(PlanMode mode, List<Ipv6Requirement> requirements, List<InputError> errors) {
        String list = listField(mode);
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < requirements.size(); i++) {
            Ipv6Requirement r = requirements.get(i);
            if (mode == PlanMode.VLAN && !ids.add(r.vlanId())) {
                errors.add(new InputError(DUPLICATE_VLAN_ID, list + "[" + i + "].id", "VLAN " + r.vlanId() + " est en double"));
                continue; // même ID → même nom par défaut : une seule erreur suffit
            }
            if (!names.add(r.name().toLowerCase(Locale.ROOT))) {
                errors.add(new InputError(DUPLICATE_NAME, list + "[" + i + "].name", "Le nom '" + r.name() + "' est utilisé deux fois"));
            }
        }
    }

    private static String listField(PlanMode mode) {
        return mode == PlanMode.VLAN ? "vlans" : "subnets";
    }

    private static void throwIfAny(List<InputError> errors) {
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }
    }
}
