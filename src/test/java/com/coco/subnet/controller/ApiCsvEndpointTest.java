package com.coco.subnet.controller;

import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class ApiCsvEndpointTest {

    @Autowired
    MockMvc mvc;

    @Test
    void downloadsTheCsvFile() throws Exception {
        MvcResult result = mvc.perform(post("/api/vlsm/csv").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"network\": \"192.168.10.0/24\", \"vlanCount\": 4, \"hostsPerVlan\": 30 }"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"plan-adressage-192.168.10.0-24.csv\"")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(body, StandardCharsets.UTF_8)).contains("\r\n40;VLAN40;30;192.168.10.192/26;");
    }

    @Test
    void errorsAreJsonEvenOnTheCsvEndpoint() throws Exception {
        mvc.perform(post("/api/vlsm/csv").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"network\": \"192.168.10.0/24\", \"vlanCount\": 0, \"hostsPerVlan\": 30 }"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VLAN_COUNT_OUT_OF_RANGE"));
    }

    @Test
    void clientAcceptingOnlyCsvStillGetsTheRightStatus() throws Exception {
        mvc.perform(post("/api/vlsm/csv").contentType(MediaType.APPLICATION_JSON).accept("text/csv")
                        .content("{ \"network\": \"300.1.1.1/24\", \"vlanCount\": 4, \"hostsPerVlan\": 30 }"))
                .andExpect(status().isBadRequest());
    }
}
