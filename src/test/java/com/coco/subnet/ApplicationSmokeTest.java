package com.coco.subnet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void swaggerUiIsServed() {
        assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void stylesheetUrlCarriesItsFingerprint() {
        String page = rest.getForObject("/", String.class);
        Matcher css = Pattern.compile("href=\"(/css/style-[0-9a-f]{32}\\.css)\"").matcher(page);

        assertThat(css.find()).as("lien vers le CSS avec empreinte").isTrue();
        assertThat(rest.getForEntity(css.group(1), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void openApiDocumentsOnlyTheApi() {
        ResponseEntity<String> docs = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody())
                .contains("Subnet Calculator API", "/api/subnets", "/api/vlsm", "/api/vlsm/csv")
                .doesNotContain("\"/vlsm\"");
    }

    @Test
    void webPagesAreServed() {
        assertThat(rest.getForEntity("/", String.class).getBody()).contains("Calcul CIDR");
        assertThat(rest.getForEntity("/vlsm", String.class).getBody()).contains("Découpage VLSM / VLAN");
    }

    @Test
    void unknownWebPageShowsTheFriendly404Page() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));

        ResponseEntity<String> response = rest.exchange("/toto", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Page introuvable");
    }

    @Test
    void unknownApiUrlIsJson() {
        ResponseEntity<String> response = rest.getForEntity("/api/toto", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"NOT_FOUND\"");
    }
}
