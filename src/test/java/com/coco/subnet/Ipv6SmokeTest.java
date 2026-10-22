package com.coco.subnet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Ipv6SmokeTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void ipv6PagesAreServed() {
        ResponseEntity<String> analysis = rest.getForEntity("/ipv6", String.class);
        ResponseEntity<String> plan = rest.getForEntity("/ipv6/plan", String.class);

        assertThat(analysis.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(analysis.getBody()).contains("Analyse d'un préfixe IPv6");
        assertThat(plan.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(plan.getBody()).contains("Plan d'adressage IPv6");
    }

    @Test
    void openApiDocumentsTheIpv6Endpoints() {
        ResponseEntity<String> docs = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody())
                .contains("/api/ipv6/subnets", "/api/ipv6/plan", "/api/ipv6/plan/csv")
                .contains("IPv6 : analyse d'un préfixe", "\"version\":\"2.0\"")
                .doesNotContain("\"/ipv6\"", "\"/ipv6/plan\"");
    }

    @Test
    void ipv6SubnetApiAnswersTheReferenceExample() {
        ResponseEntity<String> response = rest.getForEntity("/api/ipv6/subnets?cidr={cidr}", String.class,
                "2001:db8:acad:10::/64");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"networkAddress\":\"2001:db8:acad:10::\"");
    }
}
