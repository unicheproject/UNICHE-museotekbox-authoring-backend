package com.museotek.box.player;

import com.museotek.box.player.socket.BoxSocketHandler;
import com.museotek.box.player.socket.DisplaySocketHandler;
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

    public static final String DISPLAY_PATH = "/ws/display";
    public static final String BOX_PATH = "/ws/box";

    private final DisplaySocketHandler displayHandler;
    private final BoxSocketHandler boxHandler;
    private final PlayerSocketProperties properties;

    public PlayerWebSocketConfig(DisplaySocketHandler displayHandler, BoxSocketHandler boxHandler,
                                 PlayerSocketProperties properties) {
        this.displayHandler = displayHandler;
        this.boxHandler = boxHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(displayHandler, DISPLAY_PATH)
                .setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
        // Any Origin: the Box is a device, not a page, and its key is no cookie a foreign page could ride on.
        registry.addHandler(boxHandler, BOX_PATH).setAllowedOrigins("*");
    }
}
