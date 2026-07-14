package com.museotek.box.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JitUserProvisioningFilter extends OncePerRequestFilter {

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
        if (!CurrentPrincipal.isServiceAccount(jwt)) {
            provisioningService.provision(jwt);
        }
    }
}
