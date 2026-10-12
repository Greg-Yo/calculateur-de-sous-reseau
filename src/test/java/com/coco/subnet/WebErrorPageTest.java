package com.coco.subnet;

import com.coco.subnet.service.SubnetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebErrorPageTest {

    @Autowired
    TestRestTemplate rest;

    @MockitoBean
    SubnetService subnetService;

    @Test
    void unexpectedErrorShowsTheFriendly500Page() {
        when(subnetService.calculate(anyString())).thenThrow(new IllegalStateException("secret interne"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("cidr", "10.0.0.0/8");

        ResponseEntity<String> response = rest.postForEntity("/", new HttpEntity<>(form, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Erreur interne").doesNotContain("secret interne", "Exception");
    }
}
