package com.coco.subnet.controller;

import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WebController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class WebCsvAndRoutingTest {

    @Autowired
    MockMvc mvc;

    @Test
    void exportsTheCsvOfTheSubmittedPlan() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm/csv").param("network", "192.168.10.0/24")
                        .param("useVlans", "true").param("_useVlans", "on")
                        .param("rows[0].vlanId", "10").param("rows[0].name", "Compta").param("rows[0].hosts", "30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("plan-adressage-192.168.10.0-24.csv")))
                .andReturn();

        assertThat(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("10;Compta;30;192.168.10.0/26;");
    }

    @Test
    void invalidPlanReturnsToTheFormWithoutAFile() throws Exception {
        mvc.perform(post("/vlsm/csv").param("network", "300.1.1.1/24").param("_useVlans", "on")
                        .param("rows[0].name", "A").param("rows[0].hosts", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("vlsm"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "network", "OCTET_OUT_OF_RANGE"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void getOnTheCsvUrlRedirectsToTheVlsmPage() throws Exception {
        mvc.perform(get("/vlsm/csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vlsm"));
    }

    @Test
    void wrongMethodOnTheHomePageRedirectsHome() throws Exception {
        mvc.perform(put("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void unknownWebPageIs404() throws Exception {
        mvc.perform(get("/toto")).andExpect(status().isNotFound());
    }
}
