package com.coco.subnet.controller;

import com.coco.subnet.dto.CidrForm;
import com.coco.subnet.dto.SubnetResponse;
import com.coco.subnet.model.InputError;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

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
