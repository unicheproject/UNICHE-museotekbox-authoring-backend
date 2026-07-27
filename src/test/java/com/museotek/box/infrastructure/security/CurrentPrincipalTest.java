package com.museotek.box.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentPrincipalTest {

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
    void jwt_returnsEmptyWhenNoAuthenticationPresent() {
        assertThat(CurrentPrincipal.jwt()).isEmpty();
        assertThat(CurrentPrincipal.subject()).isEmpty();
    }

    @Test
    void jwt_returnsEmptyWhenAuthenticationIsNotAJwtToken() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", "n/a"));

        assertThat(CurrentPrincipal.jwt()).isEmpty();
    }

    @Test
    void jwt_andSubject_returnValuesFromJwtAuthenticationToken() {
        Jwt token = jwt("user-123", "alice");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));

        assertThat(CurrentPrincipal.jwt()).contains(token);
        assertThat(CurrentPrincipal.subject()).contains("user-123");
    }

    @Test
    void isServiceAccount_trueWhenPreferredUsernameHasServiceAccountPrefix() {
        Jwt token = jwt("service-account-museotek-box-svc", "service-account-museotek-box-svc");

        assertThat(CurrentPrincipal.isServiceAccount(token)).isTrue();
    }

    @Test
    void isServiceAccount_falseForRegularUser() {
        Jwt token = jwt("user-123", "alice");

        assertThat(CurrentPrincipal.isServiceAccount(token)).isFalse();
    }

    @Test
    void isServiceAccount_falseWhenPreferredUsernameClaimMissing() {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .claim("aud", List.of("uniche-platform"))
                .build();

        assertThat(CurrentPrincipal.isServiceAccount(token)).isFalse();
    }
}
