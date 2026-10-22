package com.coco.subnet.controller;

import com.coco.subnet.dto.RowForm;
import com.coco.subnet.dto.SubnetEntry;
import com.coco.subnet.dto.VlsmForm;
import com.coco.subnet.dto.VlsmResponse;
import com.coco.subnet.service.CsvExporter;
import com.coco.subnet.service.SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.validation.BindingResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WebController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class WebVlsmPageTest {

    @Autowired
    MockMvc mvc;

    /** Formulaire VLAN avec deux lignes : VLAN 10 (30 hôtes) et VLAN 20 « RH » (20 hôtes). */
    private static MockHttpServletRequestBuilder vlanForm(String action) {
        return vlanForm(action, "20", "30");
    }

    /**
     * Variante paramétrable. Ajouter .param() sur un champ déjà posté ajouterait une 2e valeur
     * (Spring garderait la première) : on passe donc les valeurs à modifier ici.
     */
    private static MockHttpServletRequestBuilder vlanForm(String action, String secondVlanId, String firstHosts) {
        return post("/vlsm")
                .param("network", "192.168.10.0/24")
                .param("useVlans", "true").param("_useVlans", "on")
                .param("rows[0].vlanId", "10").param("rows[0].name", "").param("rows[0].hosts", firstHosts)
                .param("rows[1].vlanId", secondVlanId).param("rows[1].name", "RH").param("rows[1].hosts", "20")
                .param("action", action);
    }

    private static Map<String, Object> modelOf(MvcResult result) {
        return result.getModelAndView().getModel();
    }

    private static VlsmForm form(MvcResult result) {
        return (VlsmForm) modelOf(result).get("vlsmForm");
    }

    @Test
    void showsTheFormWithOneVlanRow() throws Exception {
        MvcResult result = mvc.perform(get("/vlsm"))
                .andExpect(status().isOk())
                .andExpect(view().name("vlsm"))
                .andExpect(content().string(containsString("Hôtes (hors passerelle)")))
                .andReturn();

        assertThat(form(result).isUseVlans()).isTrue();
        assertThat(form(result).getRows()).hasSize(1);
        assertThat(form(result).getRows().get(0).getVlanId()).isEqualTo(10);
    }

    @Test
    void addRowProposesTheNextVlanId() throws Exception {
        MvcResult result = mvc.perform(vlanForm("addRow")).andExpect(status().isOk()).andReturn();

        assertThat(form(result).getRows()).hasSize(3);
        assertThat(form(result).getRows().get(2).getVlanId()).isEqualTo(30);
        assertThat(modelOf(result)).doesNotContainKey("plan");
    }

    @Test
    void addRowWithoutVlansHasNoId() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm").param("network", "10.0.0.0/24").param("_useVlans", "on")
                        .param("action", "addRow"))
                .andReturn();

        assertThat(form(result).isUseVlans()).isFalse();
        assertThat(form(result).getRows()).extracting(RowForm::getVlanId).containsExactly((Integer) null);
    }

    @Test
    void removeRowRemovesTheRightLine() throws Exception {
        MvcResult result = mvc.perform(vlanForm("removeRow-0")).andReturn();

        assertThat(form(result).getRows()).extracting(RowForm::getVlanId).containsExactly(20);
    }

    @Test
    void staleOrForgedRemoveRowIsIgnored() throws Exception {
        for (String action : new String[]{"removeRow-99", "removeRow--1", "removeRow-abc", "inconnu", "refresh"}) {
            MvcResult result = mvc.perform(vlanForm(action)).andExpect(status().isOk()).andReturn();

            assertThat(form(result).getRows()).as(action).hasSize(2);
        }
    }

    @Test
    void splitCreatesEqualRows() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm").param("network", "192.168.10.0/24")
                        .param("useVlans", "true").param("_useVlans", "on").param("vlanMode", "EQUAL_SPLIT")
                        .param("vlanCount", "3").param("action", "split"))
                .andExpect(content().string(containsString("Découper en parts égales")))
                .andReturn();

        assertThat(form(result).getRows()).extracting(RowForm::getVlanId).containsExactly(10, 20, 30);
        assertThat(form(result).getRows()).extracting(RowForm::getHosts).containsOnly(61L);
    }

    @Test
    void splitShowsTheFullPlanForEveryVlan() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm").param("network", "192.168.10.0/24")
                        .param("useVlans", "true").param("_useVlans", "on").param("vlanMode", "EQUAL_SPLIT")
                        .param("vlanCount", "3").param("action", "split"))
                .andExpect(content().string(containsString("192.168.10.128/26")))
                .andExpect(content().string(containsString("255.255.255.192")))
                .andExpect(content().string(containsString("0.0.0.63")))
                .andExpect(content().string(containsString("192.168.10.129")))
                .andExpect(content().string(containsString("192.168.10.191")))
                .andReturn();

        VlsmResponse plan = (VlsmResponse) modelOf(result).get("plan");
        assertThat(plan.subnets()).extracting(SubnetEntry::usableHosts).containsOnly(62L);
        assertThat(plan.remainingAddresses()).isEqualTo(64);
    }

    @Test
    void splitOnATooSmallNetworkShowsTheErrorUnderTheNetwork() throws Exception {
        MvcResult result = mvc.perform(vlanForm("split").param("vlanMode", "EQUAL_SPLIT").param("vlanCount", "100"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "network", "NETWORK_TOO_SMALL"))
                .andReturn();

        assertThat(form(result).getRows()).hasSize(2);
    }

    @Test
    void splitWithTextShowsATypeMismatch() throws Exception {
        mvc.perform(vlanForm("split").param("vlanMode", "EQUAL_SPLIT").param("vlanCount", "abc"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "vlanCount", "typeMismatch"));
    }

    @Test
    void eachModeShowsOnlyItsOwnFields() throws Exception {
        mvc.perform(vlanForm("refresh").param("vlanMode", "EQUAL_SPLIT"))
                .andExpect(content().string(containsString("Découper en parts égales")))
                .andExpect(content().string(not(containsString("Générer les lignes"))));
        mvc.perform(vlanForm("refresh").param("vlanMode", "UNIFORM"))
                .andExpect(content().string(containsString("Générer les lignes")))
                .andExpect(content().string(not(containsString("Découper en parts égales"))));
        mvc.perform(vlanForm("refresh").param("vlanMode", "CUSTOM"))
                .andExpect(content().string(not(containsString("Générer les lignes"))))
                .andExpect(content().string(not(containsString("Découper en parts égales"))));
    }

    @Test
    void generateCreatesDetailedRows() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm").param("network", "192.168.10.0/24")
                        .param("useVlans", "true").param("_useVlans", "on")
                        .param("vlanMode", "UNIFORM")
                        .param("vlanCount", "4").param("hostsPerVlan", "30").param("action", "generate"))
                .andReturn();

        assertThat(form(result).getRows()).extracting(RowForm::getVlanId).containsExactly(10, 20, 30, 40);
        assertThat(form(result).getRows()).extracting(RowForm::getName).containsExactly("VLAN10", "VLAN20", "VLAN30", "VLAN40");
        assertThat(form(result).getRows()).extracting(RowForm::getHosts).containsOnly(30L);
    }

    @Test
    void generateWithAnInvalidCountShowsTheErrorAndKeepsTheRows() throws Exception {
        MvcResult result = mvc.perform(vlanForm("generate").param("vlanCount", "0").param("hostsPerVlan", "30"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "vlanCount", "VLAN_COUNT_OUT_OF_RANGE"))
                .andReturn();

        assertThat(form(result).getRows()).hasSize(2);
    }

    @Test
    void generateWithTextShowsATypeMismatch() throws Exception {
        MvcResult result = mvc.perform(vlanForm("generate").param("vlanCount", "4").param("hostsPerVlan", "abc"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "hostsPerVlan", "typeMismatch"))
                .andReturn();

        assertThat(errorMessage(result, "hostsPerVlan")).isEqualTo("Le nombre d'hôtes par VLAN doit être un nombre entier");
        assertThat(form(result).getRows()).hasSize(2);
    }

    @Test
    void typeMismatchMessagesNameTheLineAndTheField() throws Exception {
        MvcResult result = mvc.perform(vlanForm("calculate", "abc", "x")).andReturn();

        assertThat(errorMessage(result, "rows[0].hosts")).isEqualTo("Ligne 1 : le nombre d'hôtes doit être un nombre entier");
        assertThat(errorMessage(result, "rows[1].vlanId")).isEqualTo("Ligne 2 : l'ID du VLAN doit être un nombre entier");
    }

    /** Entrée dans « Nombre de VLAN » envoie « calculate » : les lignes sont découpées d'abord. */
    @Test
    void enterInTheVlanCountSplitsBeforeCalculating() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm").param("network", "192.168.10.0/24")
                        .param("useVlans", "true").param("_useVlans", "on").param("vlanMode", "EQUAL_SPLIT")
                        .param("vlanCount", "4")
                        .param("rows[0].vlanId", "10").param("rows[0].name", "").param("rows[0].hosts", "")
                        .param("action", "calculate"))
                .andReturn();

        assertThat(form(result).getRows()).extracting(RowForm::getVlanId).containsExactly(10, 20, 30, 40);
        VlsmResponse plan = (VlsmResponse) modelOf(result).get("plan");
        assertThat(plan.subnets()).extracting(SubnetEntry::cidr)
                .containsExactly("192.168.10.0/26", "192.168.10.64/26", "192.168.10.128/26", "192.168.10.192/26");
    }

    @Test
    void enterInTheVlanCountGeneratesBeforeCalculating() throws Exception {
        MvcResult result = mvc.perform(vlanForm("calculate").param("vlanMode", "UNIFORM")
                        .param("vlanCount", "4").param("hostsPerVlan", "30"))
                .andReturn();

        assertThat(form(result).getRows()).extracting(RowForm::getName).containsExactly("VLAN10", "VLAN20", "VLAN30", "VLAN40");
        VlsmResponse plan = (VlsmResponse) modelOf(result).get("plan");
        assertThat(plan.usedAddresses()).isEqualTo(256);
    }

    @Test
    void calculateKeepsEditedRowsWhenTheirNumberMatchesTheVlanCount() throws Exception {
        MvcResult result = mvc.perform(vlanForm("calculate").param("vlanMode", "UNIFORM")
                        .param("vlanCount", "2").param("hostsPerVlan", "5"))
                .andReturn();

        VlsmResponse plan = (VlsmResponse) modelOf(result).get("plan");
        assertThat(plan.subnets()).extracting(SubnetEntry::name).containsExactly("VLAN10", "RH");
        assertThat(plan.subnets()).extracting(SubnetEntry::requestedHosts).containsExactly(30L, 20L);
    }

    @Test
    void enterWithAnInvalidVlanCountShowsTheErrorAndNoPlan() throws Exception {
        MvcResult result = mvc.perform(vlanForm("calculate").param("vlanMode", "EQUAL_SPLIT").param("vlanCount", "0"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "vlanCount", "VLAN_COUNT_OUT_OF_RANGE"))
                .andExpect(model().attributeDoesNotExist("plan"))
                .andReturn();

        assertThat(form(result).getRows()).hasSize(2);
    }

    @Test
    void customModeNeverRegeneratesTheRows() throws Exception {
        MvcResult result = mvc.perform(vlanForm("calculate").param("vlanMode", "CUSTOM").param("vlanCount", "4"))
                .andReturn();

        assertThat(form(result).getRows()).hasSize(2);
    }

    private static String errorMessage(MvcResult result, String field) {
        BindingResult binding = (BindingResult) modelOf(result).get(BindingResult.MODEL_KEY_PREFIX + "vlsmForm");
        return binding.getFieldError(field).getDefaultMessage();
    }

    @Test
    void calculateShowsThePlan() throws Exception {
        MvcResult result = mvc.perform(vlanForm("calculate"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("192.168.10.64/27")))
                .andReturn();

        VlsmResponse plan = (VlsmResponse) modelOf(result).get("plan");
        assertThat(plan.subnets()).extracting(SubnetEntry::cidr).containsExactly("192.168.10.0/26", "192.168.10.64/27");
        assertThat(plan.subnets()).extracting(SubnetEntry::gateway).containsExactly("192.168.10.1", "192.168.10.65");
    }

    @Test
    void calculateWithoutVlans() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm").param("network", "192.168.10.0/24").param("_useVlans", "on")
                        .param("rows[0].name", "LAN-A").param("rows[0].hosts", "50")
                        .param("rows[1].name", "WAN").param("rows[1].hosts", "2")
                        .param("action", "calculate"))
                .andReturn();

        VlsmResponse plan = (VlsmResponse) modelOf(result).get("plan");
        assertThat(plan.mode()).isEqualTo("REQUIREMENTS");
        assertThat(plan.subnets()).extracting(SubnetEntry::cidr).containsExactly("192.168.10.0/26", "192.168.10.64/30");
    }

    @Test
    void modelErrorsAreShownOnTheRightRowField() throws Exception {
        mvc.perform(vlanForm("calculate", "10", "30"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "rows[1].vlanId", "DUPLICATE_VLAN_ID"))
                .andExpect(model().attributeDoesNotExist("plan"))
                .andExpect(content().string(containsString("row-error")));
    }

    @Test
    void emptyFormShowsAnErrorOnEveryField() throws Exception {
        mvc.perform(post("/vlsm").param("network", "").param("useVlans", "true").param("_useVlans", "on")
                        .param("rows[0].vlanId", "").param("rows[0].hosts", "").param("action", "calculate"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "network", "EMPTY_INPUT"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "rows[0].vlanId", "MISSING_FIELD"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "rows[0].hosts", "MISSING_FIELD"))
                .andExpect(content().string(containsString("Corrigez les erreurs suivantes")));
    }

    @Test
    void textInHostsShowsOnlyTheTypeMismatch() throws Exception {
        MvcResult result = mvc.perform(vlanForm("calculate", "20", "abc"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "rows[0].hosts", "typeMismatch"))
                .andExpect(model().attributeDoesNotExist("plan"))
                .andReturn();

        assertThat(result.getModelAndView().getModel().toString()).doesNotContain("le nombre d'hôtes est obligatoire");
    }

    @Test
    void noRowsAtAll() throws Exception {
        mvc.perform(post("/vlsm").param("network", "10.0.0.0/24").param("_useVlans", "on").param("action", "calculate"))
                .andExpect(model().attributeHasFieldErrorCode("vlsmForm", "rows", "NO_REQUIREMENTS"));
    }

    @Test
    void addRowAfterVlan4090ProposesAValidId() throws Exception {
        MvcResult result = mvc.perform(post("/vlsm").param("network", "10.0.0.0/24")
                        .param("useVlans", "true").param("_useVlans", "on")
                        .param("rows[0].vlanId", "4090").param("rows[0].hosts", "5").param("action", "addRow"))
                .andReturn();

        assertThat(form(result).getRows()).extracting(RowForm::getVlanId).containsExactly(4090, 10);
    }

    @Test
    void forgedRowIndexBeyondTheBindingLimitIsNotA500() throws Exception {
        for (String url : new String[]{"/vlsm", "/vlsm/csv"}) {
            mvc.perform(post(url).param("network", "10.0.0.0/24").param("_useVlans", "on")
                            .param("rows[300].name", "A").param("rows[300].hosts", "5").param("action", "calculate"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/vlsm"));
        }
    }
}
