package com.coco.subnet.controller;

import com.coco.subnet.dto.Ipv6CidrForm;
import com.coco.subnet.dto.Ipv6SubnetResponse;
import com.coco.subnet.model.InvalidInputException;
import com.coco.subnet.service.Ipv6SubnetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** Pages web IPv6 : même principe que WebController (le modèle valide, les erreurs vont sous le champ). */
@Controller
public class Ipv6WebController {

    private final Ipv6SubnetService subnetService;

    public Ipv6WebController(Ipv6SubnetService subnetService) {
        this.subnetService = subnetService;
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
