package com.museotek.box.infrastructure.security;

import com.museotek.box.web.error.SecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Security for {@code /player/v1/**}, used only by the player project: the shared secret, no Keycloak, no CORS (the
 * caller is a server, not a browser). Ordered before {@link SecurityConfig}, which keeps every other path; curator
 * tokens are refused here, and the secret is refused there.
 */
@Configuration
public class PlayerSecurityConfig {

    @Bean
    public PlayerSecrets playerSecrets(@Value("${museotek.player.secrets}") String secrets) {
        return new PlayerSecrets(secrets);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain playerFilterChain(HttpSecurity http, PlayerSecrets secrets,
                                                 SecurityErrorHandler errorHandler) throws Exception {
        http
                .securityMatcher("/player/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(PlayerSecretFilter.ROLE))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                )
                .addFilterBefore(new PlayerSecretFilter(secrets), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
