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
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiController.class)
@Import({SubnetService.class, VlsmService.class, CsvExporter.class})
class ApiVlsmEndpointTest {

    @Autowired
    MockMvc mvc;

    private ResultActions postJson(String json) throws Exception {
        return mvc.perform(post("/api/vlsm").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void requirementsForm() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24",
                  "requirements": [ { "name": "LAN-A", "hosts": 50 }, { "name": "LAN-B", "hosts": 20 },
                                    { "name": "LAN-C", "hosts": 10 }, { "name": "WAN", "hosts": 2 } ] }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("REQUIREMENTS"))
                .andExpect(jsonPath("$.subnets[0].cidr").value("192.168.10.0/26"))
                .andExpect(jsonPath("$.subnets[3].cidr").value("192.168.10.112/30"))
                .andExpect(jsonPath("$.subnets[0].vlanId").doesNotExist())
                .andExpect(jsonPath("$.subnets[0].gateway").doesNotExist())
                .andExpect(jsonPath("$.usedAddresses").value(116))
                .andExpect(jsonPath("$.remainingAddresses").value(140));
    }

    @Test
    void vlansForm() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24",
                  "vlans": [ { "id": 10, "name": "Compta", "hosts": 50 }, { "id": 20, "hosts": 20 } ] }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("VLAN"))
                .andExpect(jsonPath("$.subnets[0].name").value("Compta"))
                .andExpect(jsonPath("$.subnets[1].name").value("VLAN20"))
                .andExpect(jsonPath("$.subnets[1].gateway").value("192.168.10.65"));
    }

    @Test
    void shortcutFormIsTheReferenceExample() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlanCount": 4, "hostsPerVlan": 30 }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subnets", hasSize(4)))
                .andExpect(jsonPath("$.subnets[0].vlanId").value(10))
                .andExpect(jsonPath("$.subnets[0].name").value("VLAN10"))
                .andExpect(jsonPath("$.subnets[0].requestedHosts").value(30))
                .andExpect(jsonPath("$.subnets[0].gateway").value("192.168.10.1"))
                .andExpect(jsonPath("$.subnets[0].cidr").value("192.168.10.0/26"))
                .andExpect(jsonPath("$.subnets[0].subnetMask").value("255.255.255.192"))
                .andExpect(jsonPath("$.subnets[0].wildcardMask").value("0.0.0.63"))
                .andExpect(jsonPath("$.subnets[0].broadcastAddress").value("192.168.10.63"))
                .andExpect(jsonPath("$.subnets[0].lastUsable").value("192.168.10.62"))
                .andExpect(jsonPath("$.subnets[0].usableHosts").value(62))
                .andExpect(jsonPath("$.subnets[0].totalAddresses").value(64))
                .andExpect(jsonPath("$.subnets[0].freeHosts").value(31))
                .andExpect(jsonPath("$.subnets[3].vlanId").value(40))
                .andExpect(jsonPath("$.usedAddresses").value(256))
                .andExpect(jsonPath("$.remainingAddresses").value(0))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    void twoFormsAtOnceIsAmbiguous() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "hosts": 5 } ], "vlanCount": 4 }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMBIGUOUS_REQUEST"));
    }

    @Test
    void noFormAtAll() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_REQUIREMENTS"));
    }

    @Test
    void shortcutNeedsBothFields() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlanCount": 4 }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("hostsPerVlan"));
    }

    @Test
    void annotationErrorUsesTheBusinessCode() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "hosts": 0 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HOSTS_NOT_POSITIVE"))
                .andExpect(jsonPath("$.errors[0].field").value("vlans[0].hosts"))
                .andExpect(jsonPath("$.errors[0].message").value("Au moins 1 hôte requis"));
    }

    @Test
    void severalAnnotationErrorsGiveValidationFailed() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 5000, "hosts": 0 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("2 erreurs dans la requête"))
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void severalModelErrorsGiveValidationFailed() throws Exception {
        postJson("""
                { "network": "300.1.1.1/24", "vlans": [ { "id": 10, "name": "a;b", "hosts": 5 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].code").value("OCTET_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.errors[1].code").value("INVALID_NAME"));
    }

    @Test
    void tooManyRowsHasTheSameCodeAsTheModel() throws Exception {
        String vlans = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "{ \"id\": " + i + ", \"hosts\": 1 }")
                .collect(Collectors.joining(","));
        postJson("{ \"network\": \"10.0.0.0/8\", \"vlans\": [" + vlans + "] }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUIREMENTS"));
    }

    @Test
    void nullElementInAListIsAMissingField() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ null ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("vlans[0]"));
    }

    @Test
    void networkTooSmallIsA400() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "requirements": [ { "name": "A", "hosts": 200 }, { "name": "B", "hosts": 50 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NETWORK_TOO_SMALL"));
    }

    @Test
    void malformedJson() throws Exception {
        postJson("{ \"network\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void textWhereANumberIsExpected() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "hosts": "abc" } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.errors[0].field").value("vlans[0].hosts"));
    }

    @Test
    void decimalHostsAreRejected() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "hosts": 2.5 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"));
    }

    @Test
    void hugeNumberIsATypeMismatch() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "hosts": 99999999999999999999 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"));
    }

    @Test
    void unknownFieldIsRejected() throws Exception {
        postJson("""
                { "network": "192.168.10.0/24", "vlans": [ { "id": 10, "host": 30 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").value("vlans[0].host"))
                .andExpect(jsonPath("$.detail").value("Champ inconnu : host"));
    }

    @Test
    void emptyBody() throws Exception {
        postJson("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_BODY"));
    }
}
