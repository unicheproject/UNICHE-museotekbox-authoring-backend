package com.museotek.box.infrastructure.security;

import com.museotek.box.application.player.VerifyDisplayTokenUseCase;
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
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Security for the player's paths, ordered before {@link SecurityConfig}, which keeps every other path.
 * {@code /display/**} is the display page's REST API: its display token, never a user login.
 * {@code /player/v1/**} is used only by the old player project: the shared secret, no Keycloak, no CORS (the caller
 * is a server, not a browser); curator tokens are refused there, and the secret is refused everywhere else.
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

    /**
     * The player's websockets ({@code /ws/**}). The handshake is open: browsers can't send a login header on it, so
     * each connection logs in with its first message ({@code hello} with a display token), checked by the player
     * package. The allowed Origins are checked by the websocket handshake itself
     * ({@code museotek.player.socket.allowed-origins}); curator tokens mean nothing here.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain playerSocketFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/ws/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * The display's REST calls ({@code /display/**}), made by the display page in a browser on the CMS's Origin, so
     * with the CMS's CORS. Logged in by its display token ({@link DisplayTokenFilter}). Media files are the
     * exception: {@code <video>} and {@code <img>} can't send the token, so their links are signed instead and
     * checked by the media query; the chain lets them through.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain displayFilterChain(HttpSecurity http, VerifyDisplayTokenUseCase verifyUseCase,
                                                  CorsConfigurationSource corsConfigurationSource,
                                                  SecurityErrorHandler errorHandler) throws Exception {
        http
                .securityMatcher("/display/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/display/media/*/content").permitAll()
                        .anyRequest().hasRole(DisplayTokenFilter.ROLE))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler.withCode(
                                "DISPLAY_TOKEN_INVALID", "This display is no longer connected; open it again from the CMS"))
                        .accessDeniedHandler(errorHandler)
                )
                .addFilterBefore(new DisplayTokenFilter(verifyUseCase), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
