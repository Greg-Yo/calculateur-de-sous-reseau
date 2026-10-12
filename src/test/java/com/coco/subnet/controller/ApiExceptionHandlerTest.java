package com.coco.subnet.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void streamedBodyCutByTheFilterIsA413() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("I/O error",
                new PayloadTooLargeException(65_536), new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ProblemDetail> response = handler.unreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().getProperties()).containsEntry("code", "PAYLOAD_TOO_LARGE");
    }

    @Test
    void rawPayloadTooLargeIsA413() {
        assertThat(handler.payloadTooLarge().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
