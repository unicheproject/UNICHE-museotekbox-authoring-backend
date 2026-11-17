package com.museotek.box.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * Logs in a device (a display or a Box) that sends its token as a Bearer token. The principal is what the verifier
 * answers: the one Box the token is tied to. A token the verifier refuses (with {@code refusal}) leaves the request
 * anonymous, and the chain refuses it. Not a bean on purpose: as a bean, Spring Boot would also run it on every request
 * outside its chain.
 */
public class DeviceTokenFilter extends OncePerRequestFilter {

    public static final String DISPLAY_ROLE = "DISPLAY";
    public static final String BOX_ROLE = "BOX";

    private static final Logger log = LoggerFactory.getLogger(DeviceTokenFilter.class);
    private static final String BEARER = "Bearer ";
    // Real tokens are 43 characters; anything far longer is not worth a database lookup.
    private static final int MAX_TOKEN_LENGTH = 64;

    private final Function<String, Object> verifier;
    private final Class<? extends RuntimeException> refusal;
    private final String role;

    public DeviceTokenFilter(Function<String, Object> verifier, Class<? extends RuntimeException> refusal, String role) {
        this.verifier = verifier;
        this.refusal = refusal;
        this.role = role;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER) && header.length() - BEARER.length() <= MAX_TOKEN_LENGTH) {
            try {
                Object device = verifier.apply(header.substring(BEARER.length()));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        device, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
                SecurityContextHolder.setContext(context);
            } catch (RuntimeException e) {
                if (!refusal.isInstance(e)) {
                    throw e;
                }
                log.debug("Refused a {} {} call: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
