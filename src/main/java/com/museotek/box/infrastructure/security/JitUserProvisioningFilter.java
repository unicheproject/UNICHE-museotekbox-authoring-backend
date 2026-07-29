package com.museotek.box.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JitUserProvisioningFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JitUserProvisioningFilter.class);

    private final JitUserProvisioningService provisioningService;

    public JitUserProvisioningFilter(JitUserProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CurrentPrincipal.jwt().ifPresent(this::maybeProvision);
        chain.doFilter(request, response);
    }

    private void maybeProvision(Jwt jwt) {
        if (CurrentPrincipal.isServiceAccount(jwt)) return;
        // Provisioning is a best-effort local mirror of the JWT identity, not a
        // dependency of any request-handling logic
        try {
            provisioningService.provision(jwt);
        } catch (RuntimeException e) {
            log.error("JIT user provisioning failed for subject '{}'; continuing request without it",
                    jwt.getSubject(), e);
        }
    }
}
