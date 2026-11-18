package com.museotek.box.player.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.display.DisplaySessionsRevokedEvent;
import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import com.museotek.box.support.FakeIdentityProvider;
import com.museotek.box.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A real websocket client against the running app: the handshake (security chain, allowed Origin), hello against the
 * database, the newest display winning, closing a revoked display, and the message size limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DisplaySocketIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static FakeIdentityProvider idp;

    @LocalServerPort
    private int port;

    @Autowired
    private BoxRepository boxRepository;

    @Autowired
    private DisplaySessionRepository sessionRepository;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeAll
    static void startFakeIdp() {
        idp = FakeIdentityProvider.start();
    }

    @AfterAll
    static void stopFakeIdp() {
        idp.stop();
    }

    @DynamicPropertySource
    static void wireIssuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> idp.issuerUri());
    }

    /** The test's side of one connection: what it received and how it was closed. */
    static final class Client extends TextWebSocketHandler {

        final BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            received.add(JSON.readTree(message.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        void hello(String token) throws Exception {
            send("{\"v\":1,\"type\":\"hello\",\"seq\":1,\"payload\":{\"token\":\"" + token + "\"}}");
        }

        JsonNode next() throws InterruptedException {
            JsonNode message = received.poll(10, TimeUnit.SECONDS);
            assertThat(message).as("a message from the tool").isNotNull();
            return message;
        }

        CloseStatus closeStatus() throws Exception {
            return closed.get(10, TimeUnit.SECONDS);
        }
    }

    private record Display(UUID sessionId, Long boxId, String token) {
    }

    private Client connect(String origin) throws Exception {
        Client client = new Client();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        URI uri = URI.create("ws://localhost:" + port + "/museotekbox/ws/display");
        client.session = new StandardWebSocketClient().execute(client, headers, uri).get(10, TimeUnit.SECONDS);
        return client;
    }

    private Long newBox() {
        Box box = new Box();
        box.setOrgId(UUID.randomUUID());
        box.setName("Hall A box");
        box.setSerialNumber("SN-" + UUID.randomUUID());
        return boxRepository.save(box).getId();
    }

    private Display newDisplay(Long boxId) {
        String token = SecretTokens.newToken();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        DisplaySession session = new DisplaySession();
        session.setId(UUID.randomUUID());
        session.setBoxId(boxId);
        session.setTokenHash(SecretTokens.sha256Hex(token));
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(1, ChronoUnit.DAYS));
        sessionRepository.save(session);
        return new Display(session.getId(), boxId, token);
    }

    private Client loggedIn(Display display) throws Exception {
        Client client = connect(ALLOWED_ORIGIN);
        client.hello(display.token());
        JsonNode reply = client.next();
        assertThat(reply.get("type").asText()).isEqualTo("hello.ok");
        assertThat(client.next().get("type").asText()).isEqualTo("box.presence");
        return client;
    }

    @Test
    void aDisplay_logsInWithItsToken_andIsTiedToItsBox() throws Exception {
        Display display = newDisplay(newBox());
        Client client = connect(ALLOWED_ORIGIN);

        client.hello(display.token());

        JsonNode reply = client.next();
        assertThat(reply.get("type").asText()).isEqualTo("hello.ok");
        assertThat(reply.get("payload").get("sessionId").asText()).isEqualTo(display.sessionId().toString());
        assertThat(reply.get("payload").get("boxId").asLong()).isEqualTo(display.boxId());
        assertThat(reply.get("sentAt").isTextual()).isTrue();
    }

    @Test
    void aWrongToken_isRefused() throws Exception {
        Client client = connect(ALLOWED_ORIGIN);

        client.hello(SecretTokens.newToken());

        assertThat(client.next().get("payload").get("code").asText()).isEqualTo("DISPLAY_TOKEN_INVALID");
        assertThat(client.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("DISPLAY_TOKEN_INVALID"));
    }

    @Test
    void aHandshakeFromAnotherOrigin_isRefused() {
        assertThatThrownBy(() -> connect("http://evil.example")).hasMessageContaining("403");
    }

    @Test
    void aSecondDisplayForTheBox_replacesTheFirst() throws Exception {
        Long boxId = newBox();
        Client first = loggedIn(newDisplay(boxId));

        loggedIn(newDisplay(boxId));

        assertThat(first.next().get("type").asText()).isEqualTo("session.replaced");
        assertThat(first.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("SESSION_REPLACED"));
    }

    @Test
    void revokingTheDisplay_closesItsConnectionAtOnce() throws Exception {
        Display display = newDisplay(newBox());
        Client client = loggedIn(display);

        transactionTemplate.executeWithoutResult(status ->
                events.publishEvent(new DisplaySessionsRevokedEvent(List.of(display.sessionId()))));

        assertThat(client.next().get("type").asText()).isEqualTo("session.revoked");
        assertThat(client.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("SESSION_REVOKED"));
    }

    @Test
    void aMessageOverTheSizeLimit_closesTheConnection() throws Exception {
        Client client = connect(ALLOWED_ORIGIN);

        client.hello("x".repeat(9000));

        assertThat(client.closeStatus().getCode()).isEqualTo(CloseStatus.TOO_BIG_TO_PROCESS.getCode());
    }
}
