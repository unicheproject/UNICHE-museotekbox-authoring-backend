package com.museotek.box.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

public final class CurrentPrincipal {

    public static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

    private CurrentPrincipal() {}

    public static Optional<Jwt> jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return Optional.of(token.getToken());
        }
        return Optional.empty();
    }

    public static Optional<String> subject() {
        return jwt().map(Jwt::getSubject);
    }

    public static boolean isServiceAccount(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        return preferredUsername != null && preferredUsername.startsWith(SERVICE_ACCOUNT_PREFIX);
    }
}
