package com.coco.subnet.controller;

import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.Ipv6CsvExporter;
import com.coco.subnet.service.Ipv6PlanService;
import com.coco.subnet.service.Ipv6SubnetService;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(Ipv6ApiController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class, Ipv6SubnetService.class,
        Ipv6PlanService.class, Ipv6CsvExporter.class})
class Ipv6ApiSubnetEndpointTest {

    @Autowired
    MockMvc mvc;

    @Test
    void analysesTheReferenceExample() throws Exception {
        mvc.perform(get("/api/ipv6/subnets").param("cidr", "2001:db8:acad:10::/64"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "cidr": "2001:db8:acad:10::/64",
                          "networkAddress": "2001:db8:acad:10::",
                          "networkAddressFull": "2001:0db8:acad:0010:0000:0000:0000:0000",
                          "lastAddress": "2001:db8:acad:10:ffff:ffff:ffff:ffff",
                          "prefixLength": 64,
                          "totalAddresses": "18446744073709551616",
                          "totalAddressesPower": "2^64",
                          "subnets64": "1",
                          "addressType": "DOCUMENTATION",
                          "addressTypeLabel": "Documentation (exemples)",
                          "warnings": []
                        }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void correctedAddressComesWithAWarning() throws Exception {
        mvc.perform(get("/api/ipv6/subnets").param("cidr", "fe80::1/64"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cidr").value("fe80::/64"))
                .andExpect(jsonPath("$.addressType").value("LINK_LOCAL"))
                .andExpect(jsonPath("$.warnings", hasItem("Adresse corrigée vers le réseau fe80::/64")));
    }

    @Test
    void prefixLongerThan64HasNoSubnets64() throws Exception {
        mvc.perform(get("/api/ipv6/subnets").param("cidr", "2001:db8:acad::/72"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subnets64").value(nullValue()))
                .andExpect(jsonPath("$.totalAddressesPower").value("2^56"));
    }

    @Test
    void prefixOutOfRangeIsAProblemDetail() throws Exception {
        mvc.perform(get("/api/ipv6/subnets").param("cidr", "2001:db8::/129"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PREFIX_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.errors[0].field").value("cidr"))
                .andExpect(jsonPath("$.errors[0].code").value("PREFIX_OUT_OF_RANGE"));
    }

    @Test
    void missingParameter() throws Exception {
        mvc.perform(get("/api/ipv6/subnets"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("cidr"));
    }

    @Test
    void csvIsNotAcceptable() throws Exception {
        mvc.perform(get("/api/ipv6/subnets").param("cidr", "2001:db8::/64").accept("text/csv"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    void wrongMethod() throws Exception {
        mvc.perform(post("/api/ipv6/subnets").param("cidr", "2001:db8::/64"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
