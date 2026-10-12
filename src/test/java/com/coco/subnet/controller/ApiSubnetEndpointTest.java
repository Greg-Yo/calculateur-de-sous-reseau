package com.coco.subnet.controller;

import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class ApiSubnetEndpointTest {

    @Autowired
    MockMvc mvc;

    @Test
    void calculatesTheReferenceExample() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", "192.168.10.0/24"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.cidr").value("192.168.10.0/24"))
                .andExpect(jsonPath("$.networkAddress").value("192.168.10.0"))
                .andExpect(jsonPath("$.subnetMask").value("255.255.255.0"))
                .andExpect(jsonPath("$.wildcardMask").value("0.0.0.255"))
                .andExpect(jsonPath("$.broadcastAddress").value("192.168.10.255"))
                .andExpect(jsonPath("$.firstUsable").value("192.168.10.1"))
                .andExpect(jsonPath("$.lastUsable").value("192.168.10.254"))
                .andExpect(jsonPath("$.usableHosts").value(254))
                .andExpect(jsonPath("$.totalAddresses").value(256))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    void wholeSpaceCountsDoNotOverflow() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", "0.0.0.0/0"))
                .andExpect(jsonPath("$.usableHosts").value(4_294_967_294L))
                .andExpect(jsonPath("$.totalAddresses").value(4_294_967_296L));
    }

    @Test
    void correctedAddressComesWithAWarning() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", "192.168.10.37/24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cidr").value("192.168.10.0/24"))
                .andExpect(jsonPath("$.warnings[0]").value("Adresse corrigée vers le réseau 192.168.10.0/24"));
    }

    @Test
    void invalidInputIsAProblemDetail() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", "300.1.1.1/24"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Entrée invalide"))
                .andExpect(jsonPath("$.code").value("OCTET_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.detail").value("L'octet 300 est hors limites (0–255)"))
                .andExpect(jsonPath("$.errors[0].field").value("cidr"))
                .andExpect(jsonPath("$.errors[0].code").value("OCTET_OUT_OF_RANGE"));
    }

    @Test
    void emptyCidrIsAnEmptyInput() throws Exception {
        mvc.perform(get("/api/subnets").param("cidr", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_INPUT"));
    }

    @Test
    void missingParameter() throws Exception {
        mvc.perform(get("/api/subnets"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("cidr"))
                .andExpect(jsonPath("$.detail").value("Paramètre obligatoire manquant : cidr"));
    }
}
