package com.museotek.box.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void noIncomingHeader_generatesNewIdAndEchoesItBackAndClearsMdcAfter() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(null);
        String[] seenDuringChain = new String[1];
        doAnswer(inv -> {
            seenDuringChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(seenDuringChain[0]).isNotBlank();
        verify(response).setHeader(CorrelationIdFilter.HEADER, seenDuringChain[0]);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void incomingHeader_isReusedAsTheRequestId() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("client-supplied-id");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(CorrelationIdFilter.HEADER, "client-supplied-id");
    }

    @Test
    void blankIncomingHeader_generatesNewIdInstead() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("   ");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(CorrelationIdFilter.HEADER),
                org.mockito.ArgumentMatchers.argThat(id -> id != null && !id.isBlank() && !id.equals("   ")));
    }

    @Test
    void mdcIsClearedEvenWhenChainThrows() {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(null);
        try {
            doAnswer(inv -> { throw new RuntimeException("downstream failure"); })
                    .when(chain).doFilter(any(), any());
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {
            // expected to propagate — this filter only guarantees MDC cleanup, not swallowing
        }
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
