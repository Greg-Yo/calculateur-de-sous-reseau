package com.coco.subnet.controller;

import com.coco.subnet.dto.Ipv6CidrForm;
import com.coco.subnet.dto.Ipv6PlanForm;
import com.coco.subnet.dto.Ipv6PlanResponse;
import com.coco.subnet.dto.Ipv6RowForm;
import com.coco.subnet.dto.Ipv6SubnetResponse;
import com.coco.subnet.model.InputError;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.model.Ipv6Plan;
import com.coco.subnet.model.Ipv6Requirement;
import com.coco.subnet.service.Ipv6CsvExporter;
import com.coco.subnet.service.Ipv6PlanService;
import com.coco.subnet.service.Ipv6SubnetService;
import com.coco.subnet.service.VlsmService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Pages web IPv6 : même principe que WebController (le modèle valide, les erreurs vont sous le champ). */
@Controller
public class Ipv6WebController {

    private final Ipv6SubnetService subnetService;
    private final Ipv6PlanService planService;
    private final VlsmService vlsmService;
    private final Ipv6CsvExporter csvExporter;

    public Ipv6WebController(Ipv6SubnetService subnetService, Ipv6PlanService planService,
                             VlsmService vlsmService, Ipv6CsvExporter csvExporter) {
        this.subnetService = subnetService;
        this.planService = planService;
        this.vlsmService = vlsmService;
        this.csvExporter = csvExporter;
    }

    @GetMapping("/ipv6")
    public String ipv6Page(Model model) {
        model.addAttribute("ipv6CidrForm", new Ipv6CidrForm());
        return "ipv6";
    }

    @PostMapping("/ipv6")
    public String calculateIpv6(@ModelAttribute("ipv6CidrForm") Ipv6CidrForm form, BindingResult binding, Model model) {
        try {
            Ipv6SubnetResponse result = Ipv6SubnetResponse.from(subnetService.calculate(form.getCidr()));
            model.addAttribute("result", result);
            model.addAttribute("totalAddressesGrouped", groupThousands(result.totalAddresses()));
            model.addAttribute("subnets64Grouped", groupThousands(result.subnets64()));
        } catch (InvalidInputException e) {
            WebController.reject(binding, e);
        }
        return "ipv6";
    }

    @GetMapping("/ipv6/plan")
    public String planPage(Model model) {
        Ipv6PlanForm form = new Ipv6PlanForm();
        form.getRows().add(new Ipv6RowForm(10, ""));
        model.addAttribute("ipv6PlanForm", form);
        return "ipv6-plan";
    }

    /** Un seul formulaire, plusieurs boutons : le paramètre « action » dit lequel a été cliqué. */
    @PostMapping("/ipv6/plan")
    public String planAction(@ModelAttribute("ipv6PlanForm") Ipv6PlanForm form, BindingResult binding,
                             @RequestParam(defaultValue = "calculate") String action, Model model) {
        switch (action) {
            case "addRow" -> form.getRows().add(
                    new Ipv6RowForm(form.isUseVlans() ? vlsmService.nextVlanId(form.vlanIds()) : null, ""));
            case "generate" -> generate(form, binding);
            case "calculate" -> calculate(form, binding).ifPresent(plan -> showPlan(plan, model));
            default -> removeRow(form, action); // "refresh" et actions inconnues : simple réaffichage
        }
        return "ipv6-plan";
    }

    /** Succès : le fichier est écrit dans la réponse (on renvoie null). Échec : retour au formulaire. */
    @PostMapping("/ipv6/plan/csv")
    public String exportCsv(@ModelAttribute("ipv6PlanForm") Ipv6PlanForm form, BindingResult binding,
                            HttpServletResponse response) throws IOException {
        Optional<Ipv6Plan> plan = calculate(form, binding);
        if (plan.isEmpty()) {
            return "ipv6-plan";
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(csvExporter.fileName(plan.get())).build().toString());
        response.getOutputStream().write(csvExporter.toBytes(plan.get()));
        return null;
    }

    /**
     * Formulaire falsifié (ex. rows[300], au-delà de la limite de liaison de Spring) :
     * on repart d'un formulaire vierge plutôt que d'afficher une erreur 500.
     */
    @ExceptionHandler(InvalidPropertyException.class)
    public String invalidFormProperty() {
        return "redirect:/ipv6/plan";
    }

    private static void showPlan(Ipv6Plan plan, Model model) {
        model.addAttribute("plan", Ipv6PlanResponse.from(plan));
        model.addAttribute("availableGrouped", groupThousands(plan.availableSubnets().toString()));
        model.addAttribute("remainingGrouped", groupThousands(plan.remainingSubnets().toString()));
    }

    /** Remplace les lignes par VLAN10, VLAN20… ; en cas d'erreur, les lignes actuelles sont gardées. */
    private void generate(Ipv6PlanForm form, BindingResult binding) {
        if (binding.hasFieldErrors("vlanCount")) {
            return;
        }
        try {
            List<Ipv6Requirement> vlans = planService.generateVlans(form.getVlanCount());
            form.setUseVlans(true);
            form.setRows(vlans.stream()
                    .map(v -> new Ipv6RowForm(v.vlanId(), v.name()))
                    .collect(Collectors.toCollection(ArrayList::new)));
        } catch (InvalidInputException e) {
            reject(binding, e);
        }
    }

    /** Lance toujours le modèle, pour afficher toutes les erreurs en une fois (même sur un formulaire vide). */
    private Optional<Ipv6Plan> calculate(Ipv6PlanForm form, BindingResult binding) {
        try {
            Ipv6Plan plan = planService.allocate(form.getNetwork(), form.getSubnetPrefix(), form.mode(),
                    form.toRequirements());
            return binding.hasErrors() ? Optional.empty() : Optional.of(plan);
        } catch (InvalidInputException e) {
            reject(binding, e);
            return Optional.empty();
        }
    }

    private static void removeRow(Ipv6PlanForm form, String action) {
        if (!action.startsWith("removeRow-")) {
            return;
        }
        try {
            int index = Integer.parseInt(action.substring("removeRow-".length()));
            if (index >= 0 && index < form.getRows().size()) {
                form.getRows().remove(index);
            }
        } catch (NumberFormatException ignored) {
            // index falsifié : on réaffiche le formulaire sans rien changer
        }
    }

    /** Place chaque erreur du modèle sous son champ, sauf si ce champ a déjà une erreur (ex. typeMismatch). */
    private static void reject(BindingResult binding, InvalidInputException e) {
        for (InputError error : e.errors()) {
            String field = toFormField(error.field());
            if (!binding.hasFieldErrors(field)) {
                binding.rejectValue(field, error.code().name(), error.message());
            }
        }
    }

    /** Noms de champs du modèle IPv6 (vlans[0].id, subnets[1].name) → noms du formulaire (rows[0].vlanId…). */
    static String toFormField(String field) {
        if (field == null) {
            return "rows";
        }
        return field.replaceFirst("^(vlans|subnets)", "rows").replace(".id", ".vlanId");
    }

    /** "18446744073709551616" → "18 446 744 073 709 551 616" (null reste null). */
    static String groupThousands(String digits) {
        if (digits == null) {
            return null;
        }
        StringBuilder grouped = new StringBuilder();
        int length = digits.length();
        for (int i = 0; i < length; i++) {
            if (i > 0 && (length - i) % 3 == 0) {
                grouped.append(' ');
            }
            grouped.append(digits.charAt(i));
        }
        return grouped.toString();
    }
}
