package com.museotek.box.player.display;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A websocket session that records what is sent to it and how it was closed. */
class FakeSession implements WebSocketSession {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, Object> attributes = new HashMap<>();
    private final List<String> sent = new ArrayList<>();
    private CloseStatus closeStatus;

    List<JsonNode> sentMessages() {
        return sent.stream().map(FakeSession::read).toList();
    }

    JsonNode lastMessage() {
        return read(sent.get(sent.size() - 1));
    }

    CloseStatus closeStatus() {
        return closeStatus;
    }

    private static JsonNode read(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) {
        sent.add(((TextMessage) message).getPayload());
    }

    @Override
    public boolean isOpen() {
        return closeStatus == null;
    }

    @Override
    public void close() {
        close(CloseStatus.NORMAL);
    }

    @Override
    public void close(CloseStatus status) {
        closeStatus = status;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getId() {
        return "fake";
    }

    @Override
    public URI getUri() {
        return URI.create("ws://localhost/ws/display");
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return new HttpHeaders();
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public String getAcceptedProtocol() {
        return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getTextMessageSizeLimit() {
        return 8192;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return 8192;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return List.of();
    }
}
