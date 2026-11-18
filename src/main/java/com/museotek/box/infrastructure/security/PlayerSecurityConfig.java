package com.museotek.box.infrastructure.security;

import com.museotek.box.application.player.VerifyBoxKeyUseCase;
import com.museotek.box.application.player.VerifyDisplayTokenUseCase;
import com.museotek.box.domain.box.InvalidBoxKeyException;
import com.museotek.box.domain.display.InvalidDisplayTokenException;
import com.museotek.box.web.error.SecurityErrorHandler;
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
 * Security for the devices' paths, ordered before {@link SecurityConfig}, which keeps every other path (the CMS, with
 * Keycloak). {@code /ws/**} are the display's and the Box's websockets, {@code /display/**} the display page's REST
 * API (its display token, never a user login), {@code /box/**} the Box's REST API (its Box key). A user's login token
 * means nothing on these paths, and a device's token nothing on the CMS's.
 */
@Configuration
public class PlayerSecurityConfig {

    /**
     * The player's websockets ({@code /ws/**}). The handshake is open: browsers can't send a login header on it, so
     * each connection logs in with its first message ({@code hello} with a display token or a Box key), checked by
     * the player package. The display's allowed Origins are checked by the websocket handshake itself
     * ({@code museotek.player.socket.allowed-origins}); curator tokens mean nothing here.
     */
    @Bean
    @Order(1)
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
     * with the CMS's CORS. Logged in by its display token ({@link DeviceTokenFilter}). Media files are the
     * exception: {@code <video>} and {@code <img>} can't send the token, so their links are signed instead and
     * checked by the media query; the chain lets them through.
     */
    @Bean
    @Order(2)
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
                        .anyRequest().hasRole(DeviceTokenFilter.DISPLAY_ROLE))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler.withCode(
                                "DISPLAY_TOKEN_INVALID", "This display is no longer connected; open it again from the CMS"))
                        .accessDeniedHandler(errorHandler)
                )
                .addFilterBefore(new DeviceTokenFilter(verifyUseCase::execute, InvalidDisplayTokenException.class,
                        DeviceTokenFilter.DISPLAY_ROLE), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /**
     * The Box's REST calls ({@code /box/**}): syncing its experiences and media. Logged in by its Box key
     * ({@link DeviceTokenFilter}). No CORS: the Box is a device, not a browser page.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain boxFilterChain(HttpSecurity http, VerifyBoxKeyUseCase verifyUseCase,
                                              SecurityErrorHandler errorHandler) throws Exception {
        http
                .securityMatcher("/box/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(DeviceTokenFilter.BOX_ROLE))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler.withCode(
                                "BOX_KEY_INVALID", "Unknown, replaced or removed box key"))
                        .accessDeniedHandler(errorHandler)
                )
                .addFilterBefore(new DeviceTokenFilter(verifyUseCase::execute, InvalidBoxKeyException.class,
                        DeviceTokenFilter.BOX_ROLE), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
