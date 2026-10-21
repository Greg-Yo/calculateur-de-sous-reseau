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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(Ipv6ApiController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class, Ipv6SubnetService.class,
        Ipv6PlanService.class, Ipv6CsvExporter.class})
class Ipv6ApiPlanEndpointTest {

    @Autowired
    MockMvc mvc;

    private ResultActions postJson(String json) throws Exception {
        return mvc.perform(post("/api/ipv6/plan").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void vlanCountFormIsTheReferenceExample() throws Exception {
        postJson("""
                { "network": "2001:db8:acad::/48", "vlanCount": 4 }
                """)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "network": "2001:db8:acad::/48",
                          "subnetPrefix": 64,
                          "mode": "VLAN",
                          "subnets": [
                            { "vlanId": 10, "name": "VLAN10", "cidr": "2001:db8:acad::/64",
                              "networkAddress": "2001:db8:acad::", "gateway": "2001:db8:acad::1",
                              "firstAddress": "2001:db8:acad::", "lastAddress": "2001:db8:acad:0:ffff:ffff:ffff:ffff" },
                            { "vlanId": 20, "name": "VLAN20", "cidr": "2001:db8:acad:1::/64",
                              "networkAddress": "2001:db8:acad:1::", "gateway": "2001:db8:acad:1::1",
                              "firstAddress": "2001:db8:acad:1::", "lastAddress": "2001:db8:acad:1:ffff:ffff:ffff:ffff" },
                            { "vlanId": 30, "name": "VLAN30", "cidr": "2001:db8:acad:2::/64",
                              "networkAddress": "2001:db8:acad:2::", "gateway": "2001:db8:acad:2::1",
                              "firstAddress": "2001:db8:acad:2::", "lastAddress": "2001:db8:acad:2:ffff:ffff:ffff:ffff" },
                            { "vlanId": 40, "name": "VLAN40", "cidr": "2001:db8:acad:3::/64",
                              "networkAddress": "2001:db8:acad:3::", "gateway": "2001:db8:acad:3::1",
                              "firstAddress": "2001:db8:acad:3::", "lastAddress": "2001:db8:acad:3:ffff:ffff:ffff:ffff" }
                          ],
                          "usedSubnets": 4,
                          "availableSubnets": "65536",
                          "remainingSubnets": "65532",
                          "warnings": []
                        }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void vlansForm() throws Exception {
        postJson("""
                { "network": "2001:db8:acad::/48", "vlans": [ { "id": 10, "name": "Compta" }, { "id": 20 } ] }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("VLAN"))
                .andExpect(jsonPath("$.subnets", hasSize(2)))
                .andExpect(jsonPath("$.subnets[0].name").value("Compta"))
                .andExpect(jsonPath("$.subnets[1].vlanId").value(20))
                .andExpect(jsonPath("$.subnets[1].name").value("VLAN20"))
                .andExpect(jsonPath("$.subnets[1].gateway").value("2001:db8:acad:1::1"));
    }

    @Test
    void subnetsFormIsThePointToPointExample() throws Exception {
        postJson("""
                { "network": "2001:db8:acad:ff::/64", "subnetPrefix": 127,
                  "subnets": [ { "name": "WAN1" }, { "name": "WAN2" } ] }
                """)
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "network": "2001:db8:acad:ff::/64",
                          "subnetPrefix": 127,
                          "mode": "REQUIREMENTS",
                          "subnets": [
                            { "vlanId": null, "name": "WAN1", "cidr": "2001:db8:acad:ff::/127",
                              "networkAddress": "2001:db8:acad:ff::", "gateway": null,
                              "firstAddress": "2001:db8:acad:ff::", "lastAddress": "2001:db8:acad:ff::1" },
                            { "vlanId": null, "name": "WAN2", "cidr": "2001:db8:acad:ff::2/127",
                              "networkAddress": "2001:db8:acad:ff::2", "gateway": null,
                              "firstAddress": "2001:db8:acad:ff::2", "lastAddress": "2001:db8:acad:ff::3" }
                          ],
                          "usedSubnets": 2,
                          "availableSubnets": "9223372036854775808",
                          "remainingSubnets": "9223372036854775806",
                          "warnings": [ "Sous-réseaux /127 : plus longs que /64, l'autoconfiguration SLAAC ne fonctionnera pas" ]
                        }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void subnetPrefixDefaultsTo64() throws Exception {
        postJson("""
                { "network": "2001:db8:acad::/48", "subnets": [ { "name": "LAN" } ] }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subnetPrefix").value(64))
                .andExpect(jsonPath("$.subnets[0].cidr").value("2001:db8:acad::/64"));
    }

    @Test
    void subnetPrefixOutOfRangeComesFromTheModel() throws Exception {
        postJson("""
                { "network": "2001:db8:acad::/48", "subnetPrefix": 40, "vlanCount": 2 }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBNET_PREFIX_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("La taille des sous-réseaux doit être comprise entre /49 et /127"));
    }

    @Test
    void twoFormsAtOnceIsAmbiguous() throws Exception {
        postJson("""
                { "network": "2001:db8:acad::/48", "vlans": [ { "id": 10 } ], "vlanCount": 4 }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMBIGUOUS_REQUEST"));
    }

    @Test
    void vlansAndSubnetsAtOnceIsAmbiguous() throws Exception {
        postJson("""
                { "network": "2001:db8:acad::/48", "vlans": [ { "id": 10 } ], "subnets": [ { "name": "A" } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMBIGUOUS_REQUEST"));
    }

    @Test
    void noFormAtAll() throws Exception {
        postJson("""
                { "network": "2001:db8:acad::/48", "subnetPrefix": 64 }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_REQUIREMENTS"));
    }

    @Test
    void nullElementInAListIsAMissingField() throws Exception {
        postJson("""
                { "network": "2001:db8:acad::/48", "vlans": [ null ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("vlans[0]"));
    }

    @Test
    void decimalSubnetPrefixIsATypeMismatch() throws Exception {
        postJson("""
                { "network": "2001:db8:acad::/48", "subnetPrefix": 64.5, "vlanCount": 4 }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"));
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        postJson("""
                { "network": "2001:db8:acad::/48", "subnetprefix": 64, "vlanCount": 4 }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("subnetprefix"));
    }

    @Test
    void tooManyRowsHasTheSameCodeAsTheModel() throws Exception {
        String subnets = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "{ \"name\": \"S" + i + "\" }")
                .collect(Collectors.joining(","));
        postJson("{ \"network\": \"2001:db8::/32\", \"subnets\": [" + subnets + "] }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUIREMENTS"));
    }

    @Test
    void invalidNetworkAndInvalidRowGiveValidationFailed() throws Exception {
        postJson("""
                { "network": "2001:db8::g/48", "vlans": [ { "id": 10, "name": "a;b" } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_CHARACTERS"))
                .andExpect(jsonPath("$.errors[0].field").value("network"))
                .andExpect(jsonPath("$.errors[1].code").value("INVALID_NAME"))
                .andExpect(jsonPath("$.errors[1].field").value("vlans[0].name"));
    }

    @Test
    void downloadsTheCsvFile() throws Exception {
        MvcResult result = mvc.perform(post("/api/ipv6/plan/csv").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"network\": \"2001:db8:acad::/48\", \"vlanCount\": 4 }"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"plan-ipv6-2001-db8-acad---48.csv\"")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(body, StandardCharsets.UTF_8))
                .contains("\r\n40;VLAN40;2001:db8:acad:3::/64;2001:db8:acad:3::1;");
    }

    @Test
    void errorsAreJsonEvenOnTheCsvEndpoint() throws Exception {
        mvc.perform(post("/api/ipv6/plan/csv").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"network\": \"2001:db8:acad::/48\", \"vlanCount\": 0 }"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VLAN_COUNT_OUT_OF_RANGE"));
    }
}
