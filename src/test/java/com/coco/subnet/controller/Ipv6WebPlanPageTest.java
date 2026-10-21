package com.coco.subnet.controller;

import com.coco.subnet.dto.Ipv6PlanForm;
import com.coco.subnet.dto.Ipv6PlanResponse;
import com.coco.subnet.dto.Ipv6RowForm;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Page « Plan IPv6 ». WebController est chargé aussi, pour vérifier que les redirections v1 sont inchangées. */
@WebMvcTest({Ipv6WebController.class, WebController.class})
@Import({Ipv6SubnetService.class, Ipv6PlanService.class, Ipv6CsvExporter.class,
        SubnetService.class, VlsmService.class, CsvExporter.class})
class Ipv6WebPlanPageTest {

    @Autowired
    MockMvc mvc;

    /** Exemple de référence de la spec : 2001:db8:acad::/48, VLAN 10 à 40, sous-réseaux /64. */
    private static MockHttpServletRequestBuilder referenceForm(String url, String action) {
        MockHttpServletRequestBuilder request = post(url)
                .param("network", "2001:db8:acad::/48").param("subnetPrefix", "64")
                .param("useVlans", "true").param("_useVlans", "on")
                .param("action", action);
        for (int i = 0; i < 4; i++) {
            request.param("rows[" + i + "].vlanId", String.valueOf((i + 1) * 10))
                    .param("rows[" + i + "].name", "VLAN" + ((i + 1) * 10));
        }
        return request;
    }

    private static Ipv6PlanForm form(MvcResult result) {
        return (Ipv6PlanForm) result.getModelAndView().getModel().get("ipv6PlanForm");
    }

    @Test
    void showsTheFormWithSubnetSizePrefilledWith64() throws Exception {
        MvcResult result = mvc.perform(get("/ipv6/plan"))
                .andExpect(status().isOk())
                .andExpect(view().name("ipv6-plan"))
                .andExpect(model().attributeDoesNotExist("plan"))
                .andExpect(content().string(containsString("Taille des sous-réseaux")))
                .andExpect(content().string(containsString("name=\"subnetPrefix\" value=\"64\"")))
                .andExpect(content().string(containsString("Utiliser des VLAN")))
                .andExpect(content().string(containsString("Nombre de VLAN")))
                .andExpect(content().string(containsString(">Générer</button>")))
                .andExpect(content().string(not(containsString("Hôtes"))))
                .andReturn();

        assertThat(form(result).getSubnetPrefix()).isEqualTo(64);
        assertThat(form(result).isUseVlans()).isTrue();
        assertThat(form(result).getRows()).hasSize(1);
        assertThat(form(result).getRows().get(0).getVlanId()).isEqualTo(10);
    }

    @Test
    void generateCreatesVlan10To40() throws Exception {
        MvcResult result = mvc.perform(post("/ipv6/plan").param("network", "2001:db8:acad::/48")
                        .param("subnetPrefix", "64").param("_useVlans", "on")
                        .param("vlanCount", "4").param("action", "generate"))
                .andExpect(status().isOk())
                .andExpect(view().name("ipv6-plan"))
                .andExpect(model().hasNoErrors())
                .andExpect(content().string(containsString("value=\"VLAN40\"")))
                .andReturn();

        Ipv6PlanForm form = form(result);
        assertThat(form.isUseVlans()).isTrue();
        assertThat(form.getRows()).extracting(Ipv6RowForm::getVlanId).containsExactly(10, 20, 30, 40);
        assertThat(form.getRows()).extracting(Ipv6RowForm::getName)
                .containsExactly("VLAN10", "VLAN20", "VLAN30", "VLAN40");
    }

    @Test
    void generateWithAnInvalidCountShowsTheErrorAndKeepsTheRows() throws Exception {
        MvcResult result = mvc.perform(referenceForm("/ipv6/plan", "generate").param("vlanCount", "101"))
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "vlanCount", "VLAN_COUNT_OUT_OF_RANGE"))
                .andReturn();
        assertThat(form(result).getRows()).hasSize(4);

        mvc.perform(referenceForm("/ipv6/plan", "generate"))
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "vlanCount", "MISSING_FIELD"));
    }

    @Test
    void generateWithTextShowsATypeMismatch() throws Exception {
        mvc.perform(referenceForm("/ipv6/plan", "generate").param("vlanCount", "abc"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "vlanCount", "typeMismatch"))
                .andExpect(content().string(containsString("Doit être un nombre entier")));
    }

    @Test
    void addRowProposesTheNextVlanId() throws Exception {
        MvcResult result = mvc.perform(referenceForm("/ipv6/plan", "addRow")).andExpect(status().isOk()).andReturn();
        assertThat(form(result).getRows()).hasSize(5);
        assertThat(form(result).getRows().get(4).getVlanId()).isEqualTo(50);
        assertThat(form(result).getRows().get(4).getName()).isEmpty();
    }

    @Test
    void addRowWithoutVlansHasNoId() throws Exception {
        MvcResult result = mvc.perform(post("/ipv6/plan").param("network", "2001:db8:acad:ff::/64")
                        .param("subnetPrefix", "127").param("_useVlans", "on")
                        .param("rows[0].name", "WAN1").param("action", "addRow"))
                .andExpect(content().string(not(containsString("ID VLAN"))))
                .andReturn();
        assertThat(form(result).getRows()).hasSize(2);
        assertThat(form(result).getRows().get(1).getVlanId()).isNull();
    }

    @Test
    void removeRowRemovesTheRightLine() throws Exception {
        MvcResult result = mvc.perform(referenceForm("/ipv6/plan", "removeRow-1")).andReturn();
        assertThat(form(result).getRows()).extracting(Ipv6RowForm::getVlanId).containsExactly(10, 30, 40);
    }

    @Test
    void staleOrForgedRemoveRowIsIgnored() throws Exception {
        for (String action : new String[]{"removeRow-99", "removeRow--1", "removeRow-abc", "inconnu", "refresh"}) {
            MvcResult result = mvc.perform(referenceForm("/ipv6/plan", action))
                    .andExpect(status().isOk())
                    .andExpect(view().name("ipv6-plan"))
                    .andExpect(model().attributeDoesNotExist("plan"))
                    .andReturn();
            assertThat(form(result).getRows()).as(action).extracting(Ipv6RowForm::getVlanId)
                    .containsExactly(10, 20, 30, 40);
        }
    }

    @Test
    void calculateShowsTheReferenceExample() throws Exception {
        MvcResult result = mvc.perform(referenceForm("/ipv6/plan", "calculate"))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attributeExists("plan"))
                .andExpect(content().string(containsString("<th>Passerelle</th>")))
                .andExpect(content().string(containsString("<td>2001:db8:acad::/64</td>")))
                .andExpect(content().string(containsString("<td>2001:db8:acad::1</td>")))
                .andExpect(content().string(containsString("<td>2001:db8:acad:0:ffff:ffff:ffff:ffff</td>")))
                .andExpect(content().string(containsString("<td>2001:db8:acad:3::/64</td>")))
                .andExpect(content().string(containsString("<td>2001:db8:acad:3::1</td>")))
                .andExpect(content().string(containsString("<td>2001:db8:acad:3:ffff:ffff:ffff:ffff</td>")))
                .andExpect(content().string(containsString("65 536")))
                .andExpect(content().string(containsString("65 532")))
                .andExpect(content().string(containsString("Exporter CSV")))
                .andExpect(content().string(not(containsString("class=\"warning\""))))
                .andReturn();

        Ipv6PlanResponse plan = (Ipv6PlanResponse) result.getModelAndView().getModel().get("plan");
        assertThat(plan.subnets()).extracting(s -> s.cidr()).containsExactly(
                "2001:db8:acad::/64", "2001:db8:acad:1::/64", "2001:db8:acad:2::/64", "2001:db8:acad:3::/64");
        assertThat(plan.usedSubnets()).isEqualTo(4);
        assertThat(plan.remainingSubnets()).isEqualTo("65532");
    }

    @Test
    void calculateWithoutVlansShowsNoVlanNorGatewayColumnAndTheWarning() throws Exception {
        mvc.perform(post("/ipv6/plan").param("network", "2001:db8:acad:ff::/64")
                        .param("subnetPrefix", "127").param("_useVlans", "on")
                        .param("rows[0].name", "WAN1").param("rows[1].name", "WAN2").param("action", "calculate"))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(content().string(containsString("<td>2001:db8:acad:ff::2/127</td>")))
                .andExpect(content().string(containsString("<td>2001:db8:acad:ff::3</td>")))
                .andExpect(content().string(not(containsString("<th>Passerelle</th>"))))
                .andExpect(content().string(not(containsString("<th>VLAN</th>"))))
                .andExpect(content().string(containsString(
                        "Sous-réseaux /127 : plus longs que /64, l&#39;autoconfiguration SLAAC ne fonctionnera pas")));
    }

    @Test
    void emptiedSubnetSizeShowsMissingFieldUnderTheField() throws Exception {
        MvcResult result = mvc.perform(post("/ipv6/plan").param("network", "2001:db8:acad::/48")
                        .param("subnetPrefix", "").param("_useVlans", "on").param("useVlans", "true")
                        .param("rows[0].vlanId", "10").param("rows[0].name", "").param("action", "calculate"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "subnetPrefix", "MISSING_FIELD"))
                .andExpect(model().attributeDoesNotExist("plan"))
                .andExpect(content().string(containsString("Le champ « Taille des sous-réseaux » est obligatoire")))
                .andReturn();
        assertThat(form(result).getSubnetPrefix()).isNull();
    }

    @Test
    void textInNumericFieldsShowsOnlyTheTypeMismatch() throws Exception {
        mvc.perform(post("/ipv6/plan").param("network", "2001:db8:acad::/48")
                        .param("subnetPrefix", "abc").param("_useVlans", "on").param("useVlans", "true")
                        .param("rows[0].vlanId", "x").param("rows[0].name", "A").param("action", "calculate"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "subnetPrefix", "typeMismatch"))
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "rows[0].vlanId", "typeMismatch"))
                .andExpect(model().attributeDoesNotExist("plan"))
                .andExpect(content().string(not(containsString("est obligatoire"))));
    }

    @Test
    void modelErrorsAreShownOnTheRightRowField() throws Exception {
        mvc.perform(post("/ipv6/plan").param("network", "2001:db8:acad::/48").param("subnetPrefix", "64")
                        .param("_useVlans", "on").param("useVlans", "true")
                        .param("rows[0].vlanId", "10").param("rows[0].name", "A")
                        .param("rows[1].vlanId", "10").param("rows[1].name", "B")
                        .param("rows[2].vlanId", "5000").param("rows[2].name", "C"))
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "rows[2].vlanId", "VLAN_ID_OUT_OF_RANGE"));

        mvc.perform(post("/ipv6/plan").param("network", "2001:db8:acad::/48").param("subnetPrefix", "64")
                        .param("_useVlans", "on").param("useVlans", "true")
                        .param("rows[0].vlanId", "10").param("rows[0].name", "A")
                        .param("rows[1].vlanId", "10").param("rows[1].name", "B"))
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "rows[1].vlanId", "DUPLICATE_VLAN_ID"));

        mvc.perform(post("/ipv6/plan").param("network", "2001:db8:acad::/48").param("subnetPrefix", "64")
                        .param("_useVlans", "on").param("rows[0].name", ""))
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "rows[0].name", "EMPTY_NAME"));
    }

    @Test
    void emptyFormShowsAnErrorOnEveryField() throws Exception {
        mvc.perform(post("/ipv6/plan").param("network", "").param("subnetPrefix", "").param("_useVlans", "on"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "network", "EMPTY_INPUT"))
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "subnetPrefix", "MISSING_FIELD"))
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "rows", "NO_REQUIREMENTS"));
    }

    @Test
    void forgedRowIndexBeyondTheBindingLimitIsNotA500() throws Exception {
        mvc.perform(post("/ipv6/plan").param("network", "2001:db8:acad::/48")
                        .param("rows[300].name", "A").param("action", "calculate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ipv6/plan"));
    }

    @Test
    void exportsTheCsvOfTheSubmittedPlan() throws Exception {
        MvcResult result = mvc.perform(referenceForm("/ipv6/plan/csv", "calculate"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("plan-ipv6-2001-db8-acad---48.csv")))
                .andReturn();

        assertThat(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .startsWith("﻿VLAN;Nom;Réseau;Passerelle;Première adresse;Dernière adresse\r\n")
                .contains("10;VLAN10;2001:db8:acad::/64;2001:db8:acad::1;2001:db8:acad::;2001:db8:acad:0:ffff:ffff:ffff:ffff\r\n");
    }

    @Test
    void invalidPlanReturnsToTheFormWithoutAFile() throws Exception {
        mvc.perform(post("/ipv6/plan/csv").param("network", "2001:db8::g/48").param("subnetPrefix", "128")
                        .param("_useVlans", "on").param("rows[0].name", "A"))
                .andExpect(status().isOk())
                .andExpect(view().name("ipv6-plan"))
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "network", "INVALID_CHARACTERS"))
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "subnetPrefix", "SUBNET_PREFIX_OUT_OF_RANGE"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void getOnTheCsvUrlRedirectsToThePlanPage() throws Exception {
        mvc.perform(get("/ipv6/plan/csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ipv6/plan"));
    }

    @Test
    void wrongMethodOnIpv6PagesRedirectsToTheRightPage() throws Exception {
        mvc.perform(put("/ipv6")).andExpect(redirectedUrl("/ipv6"));
        mvc.perform(put("/ipv6/plan")).andExpect(redirectedUrl("/ipv6/plan"));
    }

    @Test
    void v1RedirectsAreUnchanged() throws Exception {
        mvc.perform(get("/vlsm/csv")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/vlsm"));
        mvc.perform(put("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        mvc.perform(put("/vlsm")).andExpect(redirectedUrl("/vlsm"));
    }

    @Test
    void redirectTargetFollowsTheClosestPage() {
        assertThat(RoutingErrorHandler.redirectTarget("/ipv6/plan/csv")).isEqualTo("/ipv6/plan");
        assertThat(RoutingErrorHandler.redirectTarget("/ipv6")).isEqualTo("/ipv6");
        assertThat(RoutingErrorHandler.redirectTarget("/vlsm/csv")).isEqualTo("/vlsm");
        assertThat(RoutingErrorHandler.redirectTarget("/")).isEqualTo("/");
    }

    @Test
    void mapsModelFieldNamesToFormFieldNames() {
        assertThat(Ipv6WebController.toFormField("vlans[2].id")).isEqualTo("rows[2].vlanId");
        assertThat(Ipv6WebController.toFormField("subnets[1].name")).isEqualTo("rows[1].name");
        assertThat(Ipv6WebController.toFormField("subnetPrefix")).isEqualTo("subnetPrefix");
        assertThat(Ipv6WebController.toFormField(null)).isEqualTo("rows");
    }

    @Test
    void escapesTheUserInput() throws Exception {
        mvc.perform(post("/ipv6/plan").param("network", "2001:db8::/48").param("subnetPrefix", "64")
                        .param("_useVlans", "on").param("rows[0].name", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("ipv6PlanForm", "rows[0].name", "INVALID_NAME"))
                .andExpect(content().string(not(containsString("<script>"))));
    }
}
