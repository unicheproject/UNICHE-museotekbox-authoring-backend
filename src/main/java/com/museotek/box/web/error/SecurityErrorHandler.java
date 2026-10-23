package com.museotek.box.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.infrastructure.logging.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Writes Spring Security's own 401 and 403 answers as an {@link ErrorEnvelope}, the same body
 * {@link GlobalExceptionHandler} gives every other error. These happen in the filter chain,
 * before any controller, so the advice never sees them.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    // The Bearer defaults still set the status and the WWW-Authenticate header (RFC 6750),
    // which says why the token was refused; only the empty body is replaced.
    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        bearerEntryPoint.commence(request, response, e);
        write(response, "UNAUTHENTICATED", "A valid access token is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        bearerAccessDenied.handle(request, response, e);
        write(response, "FORBIDDEN", "Access denied");
    }

    private void write(HttpServletResponse response, String code, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorEnvelope body = new ErrorEnvelope(code, message, List.of(), MDC.get(CorrelationIdFilter.MDC_KEY));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
