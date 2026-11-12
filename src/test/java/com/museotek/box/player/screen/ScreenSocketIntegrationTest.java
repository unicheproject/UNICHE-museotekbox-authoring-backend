package com.museotek.box.player.screen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.screen.ScreenRevokedEvent;
import com.museotek.box.application.screen.ScreenSecrets;
import com.museotek.box.domain.screen.ScreenSetupCode;
import com.museotek.box.infrastructure.repository.ScreenSetupCodeRepository;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A real websocket client against the running app: the handshake (security chain, allowed Origin), SETUP and AUTH
 * against the database, closing a removed screen, and the message size limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ScreenSocketIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static FakeIdentityProvider idp;

    @LocalServerPort
    private int port;

    @Autowired
    private ScreenSetupCodeRepository setupCodeRepository;

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

        JsonNode next() throws InterruptedException {
            JsonNode message = received.poll(10, TimeUnit.SECONDS);
            assertThat(message).as("a message from the tool").isNotNull();
            return message;
        }

        CloseStatus closeStatus() throws Exception {
            return closed.get(10, TimeUnit.SECONDS);
        }
    }

    private Client connect(String origin) throws Exception {
        Client client = new Client();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        URI uri = URI.create("ws://localhost:" + port + "/museotekbox/player/ws/screen");
        client.session = new StandardWebSocketClient().execute(client, headers, uri).get(10, TimeUnit.SECONDS);
        return client;
    }

    private String newSetupCode() {
        String code = ScreenSecrets.newSetupCode();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ScreenSetupCode setupCode = new ScreenSetupCode();
        setupCode.setId(UUID.randomUUID());
        setupCode.setOrgId(UUID.randomUUID());
        setupCode.setScreenName("Hall A screen");
        setupCode.setCodeHash(ScreenSecrets.sha256Hex(ScreenSecrets.normaliseSetupCode(code)));
        setupCode.setCreatedAt(now);
        setupCode.setExpiresAt(now.plus(1, ChronoUnit.DAYS));
        setupCodeRepository.save(setupCode);
        return code;
    }

    private JsonNode setUp(Client client) throws Exception {
        client.send("{\"type\":\"SETUP\",\"setupCode\":\"" + newSetupCode() + "\"}");
        JsonNode reply = client.next();
        assertThat(reply.get("type").asText()).isEqualTo("SETUP_OK");
        return reply;
    }

    @Test
    void aNewScreen_setsUpWithItsCode_thenLogsInWithItsKey() throws Exception {
        Client first = connect(ALLOWED_ORIGIN);
        JsonNode setup = setUp(first);
        first.session.close();

        Client second = connect(ALLOWED_ORIGIN);
        second.send("{\"type\":\"AUTH\",\"screenKey\":\"" + setup.get("screenKey").asText() + "\"}");

        JsonNode auth = second.next();
        assertThat(auth.get("type").asText()).isEqualTo("AUTH_OK");
        assertThat(auth.get("screenId").asText()).isEqualTo(setup.get("screenId").asText());
        assertThat(auth.get("screenName").asText()).isEqualTo("Hall A screen");
    }

    @Test
    void aHandshakeFromAnotherOrigin_isRefused() {
        assertThatThrownBy(() -> connect("http://evil.example")).hasMessageContaining("403");
    }

    @Test
    void removingTheScreen_closesItsConnectionAtOnce() throws Exception {
        Client client = connect(ALLOWED_ORIGIN);
        UUID screenId = UUID.fromString(setUp(client).get("screenId").asText());

        transactionTemplate.executeWithoutResult(status -> events.publishEvent(new ScreenRevokedEvent(screenId)));

        assertThat(client.next().get("code").asText()).isEqualTo("SCREEN_KEY_INVALID");
        assertThat(client.closeStatus().getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
    }

    @Test
    void aMessageOverTheSizeLimit_closesTheConnection() throws Exception {
        Client client = connect(ALLOWED_ORIGIN);

        client.send("{\"type\":\"SETUP\",\"setupCode\":\"" + "x".repeat(9000) + "\"}");

        assertThat(client.closeStatus().getCode()).isEqualTo(CloseStatus.TOO_BIG_TO_PROCESS.getCode());
    }
}
