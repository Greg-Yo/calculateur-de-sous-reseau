package com.coco.subnet.controller;

import com.coco.subnet.service.Ipv6SubnetService;
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
@Import(Ipv6SubnetService.class)
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
    void groupsThousandsWithSpaces() {
        assertThat(Ipv6WebController.groupThousands("18446744073709551616")).isEqualTo("18 446 744 073 709 551 616");
        assertThat(Ipv6WebController.groupThousands("65536")).isEqualTo("65 536");
        assertThat(Ipv6WebController.groupThousands("256")).isEqualTo("256");
        assertThat(Ipv6WebController.groupThousands("1")).isEqualTo("1");
        assertThat(Ipv6WebController.groupThousands(null)).isNull();
    }
}
