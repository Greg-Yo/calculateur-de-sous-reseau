package com.coco.subnet.controller;

import com.coco.subnet.dto.CidrForm;
import com.coco.subnet.dto.RowForm;
import com.coco.subnet.dto.SubnetResponse;
import com.coco.subnet.dto.VlsmForm;
import com.coco.subnet.dto.VlsmResponse;
import com.coco.subnet.model.InputError;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.model.Requirement;
import com.coco.subnet.model.VlsmPlan;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
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

/** Pages web : chaque action remplit le Model, puis renvoie le nom de la vue Thymeleaf. */
@Controller
public class WebController {

    private final SubnetService subnetService;
    private final VlsmService vlsmService;
    private final CsvExporter csvExporter;

    public WebController(SubnetService subnetService, VlsmService vlsmService, CsvExporter csvExporter) {
        this.subnetService = subnetService;
        this.vlsmService = vlsmService;
        this.csvExporter = csvExporter;
    }

    @GetMapping("/")
    public String cidrPage(Model model) {
        model.addAttribute("cidrForm", new CidrForm());
        return "index";
    }

    @PostMapping("/")
    public String calculateCidr(@ModelAttribute("cidrForm") CidrForm form, BindingResult binding, Model model) {
        try {
            model.addAttribute("result", SubnetResponse.from(subnetService.calculate(form.getCidr())));
        } catch (InvalidInputException e) {
            reject(binding, e);
        }
        return "index";
    }

    @GetMapping("/vlsm")
    public String vlsmPage(Model model) {
        VlsmForm form = new VlsmForm();
        form.getRows().add(new RowForm(10, "", null));
        model.addAttribute("vlsmForm", form);
        return "vlsm";
    }

    /** Un seul formulaire, plusieurs boutons : le paramètre « action » dit lequel a été cliqué. */
    @PostMapping("/vlsm")
    public String vlsmAction(@ModelAttribute("vlsmForm") VlsmForm form, BindingResult binding,
                             @RequestParam(defaultValue = "calculate") String action, Model model) {
        switch (action) {
            case "addRow" -> form.getRows().add(
                    new RowForm(form.isUseVlans() ? vlsmService.nextVlanId(form.vlanIds()) : null, "", null));
            case "generate" -> generate(form, binding);
            case "calculate" -> calculate(form, binding)
                    .ifPresent(plan -> model.addAttribute("plan", VlsmResponse.from(plan)));
            default -> removeRow(form, action); // "refresh" et actions inconnues : simple réaffichage
        }
        return "vlsm";
    }

    /** Succès : le fichier est écrit dans la réponse (on renvoie null). Échec : retour au formulaire. */
    @PostMapping("/vlsm/csv")
    public String exportCsv(@ModelAttribute("vlsmForm") VlsmForm form, BindingResult binding,
                            HttpServletResponse response) throws IOException {
        Optional<VlsmPlan> plan = calculate(form, binding);
        if (plan.isEmpty()) {
            return "vlsm";
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(csvExporter.fileName(plan.get())).build().toString());
        response.getOutputStream().write(csvExporter.toBytes(plan.get()));
        return null;
    }

    private void generate(VlsmForm form, BindingResult binding) {
        if (binding.hasFieldErrors("vlanCount") || binding.hasFieldErrors("hostsPerVlan")) {
            return;
        }
        try {
            List<Requirement> vlans = vlsmService.generateUniformVlans(form.getVlanCount(), form.getHostsPerVlan());
            form.setUseVlans(true);
            form.setRows(vlans.stream()
                    .map(v -> new RowForm(v.vlanId(), v.name(), v.hosts()))
                    .collect(Collectors.toCollection(ArrayList::new)));
        } catch (InvalidInputException e) {
            reject(binding, e);
        }
    }

    /** Lance toujours le modèle, pour afficher toutes les erreurs en une fois (même sur un formulaire vide). */
    private Optional<VlsmPlan> calculate(VlsmForm form, BindingResult binding) {
        try {
            VlsmPlan plan = vlsmService.allocate(form.getNetwork(), form.mode(), form.toRequirements());
            return binding.hasErrors() ? Optional.empty() : Optional.of(plan);
        } catch (InvalidInputException e) {
            reject(binding, e);
            return Optional.empty();
        }
    }

    private static void removeRow(VlsmForm form, String action) {
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

    /**
     * Formulaire falsifié (ex. rows[300], au-delà de la limite de liaison de Spring) :
     * on repart d'un formulaire vierge plutôt que d'afficher une erreur 500.
     */
    @ExceptionHandler(InvalidPropertyException.class)
    public String invalidFormProperty() {
        return "redirect:/vlsm";
    }

    /** Place chaque erreur du modèle sous son champ, sauf si ce champ a déjà une erreur (ex. typeMismatch). */
    static void reject(BindingResult binding, InvalidInputException e) {
        for (InputError error : e.errors()) {
            String field = toFormField(error.field());
            if (!binding.hasFieldErrors(field)) {
                binding.rejectValue(field, error.code().name(), error.message());
            }
        }
    }

    /** Noms de champs du modèle (vlans[0].id) → noms du formulaire web (rows[0].vlanId). */
    static String toFormField(String field) {
        if (field == null) {
            return "rows";
        }
        return field.replaceFirst("^(vlans|requirements)", "rows").replace(".id", ".vlanId");
    }
}
