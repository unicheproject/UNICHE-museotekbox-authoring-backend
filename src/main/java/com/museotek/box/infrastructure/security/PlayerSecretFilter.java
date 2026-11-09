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

/**
 * Logs in the player project when a {@code /player/v1} request carries one of the shared secrets as a Bearer token.
 * Anything else stays anonymous and is refused by the chain. Not a bean on purpose: as a bean, Spring Boot would
 * also run it on every request outside the player chain.
 */
public class PlayerSecretFilter extends OncePerRequestFilter {

    public static final String ROLE = "PLAYER";
    static final String PRINCIPAL = "museotek-player";

    private static final Logger log = LoggerFactory.getLogger(PlayerSecretFilter.class);
    private static final String BEARER = "Bearer ";

    private final PlayerSecrets secrets;

    public PlayerSecretFilter(PlayerSecrets secrets) {
        this.secrets = secrets;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            if (secrets.matches(header.substring(BEARER.length()))) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
                SecurityContextHolder.setContext(context);
            } else {
                log.warn("Refused a {} {} call with a wrong player secret", request.getMethod(), request.getRequestURI());
            }
        }
        chain.doFilter(request, response);
    }
}
