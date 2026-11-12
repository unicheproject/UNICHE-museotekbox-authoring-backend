package com.museotek.box.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

// HIGHEST_PRECEDENCE so this runs before Spring Security's own filter chain too — every
// log line for a request, including ones Spring Security itself emits, gets the same id.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER = "X-Request-Id";

    // An incoming id is reused only if it is short and plain, so a client can't write line breaks
    // or long junk into our logs through this header.
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = chooseId(request.getHeader(HEADER));
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are pooled and reused: without this, a thread that handled
            // this request could log a later, unrelated request under this id.
            MDC.remove(MDC_KEY);
        }
    }

    static String chooseId(String incoming) {
        if (incoming != null && SAFE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
