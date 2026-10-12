package com.coco.subnet.controller;

import com.coco.subnet.dto.SubnetResponse;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WebController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class WebCidrPageTest {

    @Autowired
    MockMvc mvc;

    @Test
    void showsTheEmptyForm() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("cidrForm"))
                .andExpect(model().attributeDoesNotExist("result"))
                .andExpect(content().string(containsString("Calcul CIDR")));
    }

    @Test
    void showsTheResult() throws Exception {
        MvcResult result = mvc.perform(post("/").param("cidr", "192.168.10.0/24"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("192.168.10.255")))
                .andReturn();

        SubnetResponse response = (SubnetResponse) result.getModelAndView().getModel().get("result");
        assertThat(response.usableHosts()).isEqualTo(254);
    }

    @Test
    void showsTheCorrectionWarning() throws Exception {
        mvc.perform(post("/").param("cidr", "192.168.10.37/24"))
                .andExpect(content().string(containsString("Adresse corrigée vers le réseau 192.168.10.0/24")));
    }

    @Test
    void showsTheErrorUnderTheFieldAndKeepsTheInput() throws Exception {
        mvc.perform(post("/").param("cidr", "300.1.1.1/24"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("cidrForm", "cidr", "OCTET_OUT_OF_RANGE"))
                .andExpect(model().attributeDoesNotExist("result"))
                .andExpect(content().string(containsString("octet 300 est hors limites")))
                .andExpect(content().string(containsString("value=\"300.1.1.1/24\"")));
    }

    @Test
    void emptyFormShowsAMessage() throws Exception {
        mvc.perform(post("/").param("cidr", ""))
                .andExpect(model().attributeHasFieldErrorCode("cidrForm", "cidr", "EMPTY_INPUT"));
    }

    @Test
    void mapsModelFieldsToFormFields() {
        assertThat(WebController.toFormField("vlans[2].id")).isEqualTo("rows[2].vlanId");
        assertThat(WebController.toFormField("requirements[0].hosts")).isEqualTo("rows[0].hosts");
        assertThat(WebController.toFormField("vlans")).isEqualTo("rows");
        assertThat(WebController.toFormField("network")).isEqualTo("network");
        assertThat(WebController.toFormField(null)).isEqualTo("rows");
    }
}
