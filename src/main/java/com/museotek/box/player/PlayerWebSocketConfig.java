package com.museotek.box.player;

import com.museotek.box.player.screen.ScreenSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * The player's websocket addresses. Plain websockets with JSON messages (no STOMP). The handshake carries no login:
 * browsers can't set headers on it, so each connection logs in with its first message. Its security chain is
 * {@code PlayerSecurityConfig.playerSocketFilterChain}.
 */
@Configuration
@EnableWebSocket
public class PlayerWebSocketConfig implements WebSocketConfigurer {

    public static final String SCREEN_PATH = "/player/ws/screen";

    private final ScreenSocketHandler screenHandler;
    private final PlayerSocketProperties properties;

    public PlayerWebSocketConfig(ScreenSocketHandler screenHandler, PlayerSocketProperties properties) {
        this.screenHandler = screenHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(screenHandler, SCREEN_PATH)
                .setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
    }
}
