package com.relivus.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceId 过滤器测试：透传、生成、超长重建、请求后清理 MDC。
 */
class TraceIdFilterTest {

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void propagatesClientTraceId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isEqualTo("trace-abc-123");
        });

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("trace-abc-123");
        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void generatesTraceIdWhenHeaderMissing() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] captured = new String[1];

        filter.doFilter(request, response, (req, res) ->
                captured[0] = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));

        assertThat(captured[0]).isNotBlank();
        assertThat(captured[0]).matches("[0-9a-f]{32}");
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(captured[0]);
    }

    @Test
    void blankOrTooLongTraceIdIsRegenerated() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        for (String bad : new String[]{"   ", "x".repeat(65)}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
            request.addHeader(TraceIdFilter.TRACE_ID_HEADER, bad);
            MockHttpServletResponse response = new MockHttpServletResponse();
            String[] captured = new String[1];
            filter.doFilter(request, response, (req, res) ->
                    captured[0] = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
            assertThat(captured[0]).isNotBlank();
            assertThat(captured[0]).isNotEqualTo(bad.trim());
            assertThat(captured[0].length()).isBetween(1, 64);
        }
    }

    @Test
    void cleansMdcOnException() {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                filter.doFilter(request, response, (req, res) -> {
                    throw new IllegalStateException("boom");
                }));
        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}