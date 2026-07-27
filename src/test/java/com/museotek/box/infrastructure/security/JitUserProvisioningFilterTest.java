package com.museotek.box.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JitUserProvisioningFilterTest {

    private final JitUserProvisioningService provisioningService = mock(JitUserProvisioningService.class);
    private final JitUserProvisioningFilter filter = new JitUserProvisioningFilter(provisioningService);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwt(String subject, String preferredUsername) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", preferredUsername)
                .claim("aud", List.of("uniche-platform"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void humanUser_isProvisionedAndChainContinues() throws Exception {
        Jwt token = jwt("user-1", "alice");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));

        filter.doFilter(request, response, chain);

        verify(provisioningService).provision(token);
        verify(chain).doFilter(request, response);
    }

    @Test
    void serviceAccount_isNotProvisionedButChainStillContinues() throws Exception {
        Jwt token = jwt("service-account-museotek-box-svc", "service-account-museotek-box-svc");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));

        filter.doFilter(request, response, chain);

        verify(provisioningService, never()).provision(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void noAuthentication_skipsProvisioningAndChainStillContinues() throws Exception {
        // e.g. a permitAll() endpoint (swagger, actuator/health) with no Bearer token at all.
        filter.doFilter(request, response, chain);

        verifyNoInteractions(provisioningService);
        verify(chain).doFilter(request, response);
    }

    @Test
    void provisioningFailure_propagatesAndAbortsChain() throws Exception {
        // Documents the current gap: a DB failure during JIT provisioning is not caught
        // here, so it never reaches GlobalExceptionHandler's ErrorEnvelope shape — it
        // propagates straight out of the filter chain.
        Jwt token = jwt("user-1", "alice");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
        doThrow(new RuntimeException("db down")).when(provisioningService).provision(token);

        try {
            filter.doFilter(request, response, chain);
            org.assertj.core.api.Assertions.fail("expected the provisioning failure to propagate");
        } catch (RuntimeException expected) {
            org.assertj.core.api.Assertions.assertThat(expected).hasMessage("db down");
        }
        verify(chain, never()).doFilter(request, response);
    }
}
