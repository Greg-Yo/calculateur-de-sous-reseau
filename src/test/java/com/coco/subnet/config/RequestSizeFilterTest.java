package com.coco.subnet.config;

import com.coco.subnet.controller.PayloadTooLargeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestSizeFilterTest {

    private final RequestSizeFilter filter = new RequestSizeFilter(new ObjectMapper());

    /** Requête « chunked » : la taille n'est pas annoncée dans Content-Length. */
    private static MockHttpServletRequest chunked(int size) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/vlsm") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[size]);
        return request;
    }

    @Test
    void streamedBodyOverTheLimitIsCut() {
        FilterChain readAll = (req, res) -> req.getInputStream().readAllBytes();

        assertThatThrownBy(() -> filter.doFilter(chunked(70_000), new MockHttpServletResponse(), readAll))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void streamedBodyUnderTheLimitPasses() throws Exception {
        int[] read = new int[1];
        FilterChain readAll = (req, res) -> read[0] = req.getInputStream().readAllBytes().length;

        filter.doFilter(chunked(60_000), new MockHttpServletResponse(), readAll);

        assertThat(read[0]).isEqualTo(60_000);
    }

    @Test
    void singleByteReadsAreCountedToo() {
        FilterChain readByByte = (req, res) -> {
            while (req.getInputStream().read() != -1) {
                // lecture octet par octet
            }
        };

        assertThatThrownBy(() -> filter.doFilter(chunked(70_000), new MockHttpServletResponse(), readByByte))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void webPagesAreNotFiltered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/vlsm");
        request.setContent(new byte[70_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> req.getInputStream().readAllBytes());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
