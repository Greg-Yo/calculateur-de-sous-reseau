package com.coco.subnet.controller;

import com.coco.subnet.service.Ipv6CsvExporter;
import com.coco.subnet.service.Ipv6PlanService;
import com.coco.subnet.service.Ipv6SubnetService;
import com.coco.subnet.service.VlsmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(Ipv6WebController.class)
@Import({Ipv6SubnetService.class, Ipv6PlanService.class, VlsmService.class, Ipv6CsvExporter.class})
class Ipv6WebPagesTest {

    @Autowired
    MockMvc mvc;

    @Test
    void showsTheEmptyForm() throws Exception {
        mvc.perform(get("/ipv6"))
                .andExpect(status().isOk())
                .andExpect(view().name("ipv6"))
                .andExpect(model().attributeExists("ipv6CidrForm"))
                .andExpect(model().attributeDoesNotExist("result"))
                .andExpect(content().string(containsString("Analyse d'un préfixe IPv6</h1>")))
                .andExpect(content().string(containsString("href=\"/ipv6/plan\"")));
    }

    @Test
    void showsTheReferenceExample() throws Exception {
        mvc.perform(post("/ipv6").param("cidr", "2001:db8:acad:10::/64"))
                .andExpect(status().isOk())
                .andExpect(view().name("ipv6"))
                .andExpect(model().attributeExists("result"))
                .andExpect(content().string(containsString("2001:db8:acad:10::/64")))
                .andExpect(content().string(containsString("2001:0db8:acad:0010:0000:0000:0000:0000")))
                .andExpect(content().string(containsString("2001:db8:acad:10:ffff:ffff:ffff:ffff")))
                .andExpect(content().string(containsString("2^64 = 18 446 744 073 709 551 616")))
                .andExpect(content().string(containsString("Documentation (exemples)")))
                .andExpect(content().string(not(containsString("Adresse corrigée"))));
    }

    @Test
    void showsNotApplicableWhenPrefixIsLongerThan64() throws Exception {
        mvc.perform(post("/ipv6").param("cidr", "2001:db8::/127"))
                .andExpect(content().string(containsString("2^1 = 2")))
                .andExpect(content().string(containsString("sans objet")));
    }

    @Test
    void groupsTheNumberOf64Subnets() throws Exception {
        mvc.perform(post("/ipv6").param("cidr", "fd00:1234::/48"))
                .andExpect(content().string(containsString("65 536")))
                .andExpect(content().string(containsString("Unique locale (privée)")));
    }

    @Test
    void showsTheCorrectionBanner() throws Exception {
        mvc.perform(post("/ipv6").param("cidr", "2001:db8:acad:10::25/64"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"warning\"")))
                .andExpect(content().string(containsString("Adresse corrigée vers le réseau 2001:db8:acad:10::/64")));
    }

    @Test
    void showsTheErrorUnderTheFieldAndKeepsTheInput() throws Exception {
        mvc.perform(post("/ipv6").param("cidr", "2001:db8::/129"))
                .andExpect(status().isOk())
                .andExpect(view().name("ipv6"))
                .andExpect(model().attributeHasFieldErrorCode("ipv6CidrForm", "cidr", "PREFIX_OUT_OF_RANGE"))
                .andExpect(model().attributeDoesNotExist("result"))
                .andExpect(content().string(containsString("Le préfixe doit être compris entre 0 et 128")))
                .andExpect(content().string(containsString("value=\"2001:db8::/129\"")));
    }

    @Test
    void emptyFormShowsAMessage() throws Exception {
        mvc.perform(post("/ipv6").param("cidr", ""))
                .andExpect(model().attributeHasFieldErrorCode("ipv6CidrForm", "cidr", "EMPTY_INPUT"));
    }

    @Test
    void groupsTheWholeAddressSpace() throws Exception {
        mvc.perform(post("/ipv6").param("cidr", "::/0"))
                .andExpect(content().string(containsString(
                        "2^128 = 340 282 366 920 938 463 463 374 607 431 768 211 456")))
                .andExpect(content().string(containsString("<td>18 446 744 073 709 551 616</td>")));
    }

    @Test
    void escapesTheUserInput() throws Exception {
        mvc.perform(post("/ipv6").param("cidr", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("ipv6CidrForm", "cidr", "INVALID_CHARACTERS"))
                .andExpect(content().string(containsString("value=\"&lt;script&gt;alert(1)&lt;/script&gt;\"")))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    @Test
    void footerMentionsIpv6() throws Exception {
        mvc.perform(get("/ipv6"))
                .andExpect(content().string(containsString("IPv4 · IPv6 · CIDR · VLSM · VLAN")));
    }

    @Test
    void groupsThousandsWithSpaces() {
        assertThat(Ipv6WebController.groupThousands("18446744073709551616")).isEqualTo("18 446 744 073 709 551 616");
        assertThat(Ipv6WebController.groupThousands("65536")).isEqualTo("65 536");
        assertThat(Ipv6WebController.groupThousands("256")).isEqualTo("256");
        assertThat(Ipv6WebController.groupThousands("1")).isEqualTo("1");
        assertThat(Ipv6WebController.groupThousands(null)).isNull();
    }
}
