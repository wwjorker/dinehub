package com.dinehub.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdFilterTest {

    @Test
    void propagatesSafeRequestIdAndClearsThreadContext() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "trace_123456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertEquals("trace_123456", MDC.get("requestId")));

        assertEquals("trace_123456", response.getHeader(RequestIdFilter.HEADER));
        assertNull(MDC.get("requestId"));
    }
}
