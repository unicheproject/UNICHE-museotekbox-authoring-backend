package com.museotek.box.player.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.museotek.box.application.box.BoxKeyChangedEvent;
import com.museotek.box.application.box.BoxesNeedSyncEvent;
import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxStatus;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.domain.project.Project;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.player.socket.DisplaySocketIntegrationTest.Client;
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
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real Box and display against the running app: the Box logs in with its key (from any Origin), goes online and
 * offline in the database, hears about its display and about syncing, and is closed when its key is replaced.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BoxSocketIntegrationTest {

    private static FakeIdentityProvider idp;

    @LocalServerPort
    private int port;

    @Autowired
    private BoxRepository boxRepository;
    @Autowired
    private DisplaySessionRepository sessionRepository;
    @Autowired
    private ProjectRepository projectRepository;
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

    private record KeyedBox(Long id, String key) {
    }

    private KeyedBox newBox() {
        String key = SecretTokens.newToken();
        Box box = new Box();
        box.setOrgId(UUID.randomUUID());
        box.setName("Hall A box");
        box.setSerialNumber("SN-" + UUID.randomUUID());
        box.setKeyHash(SecretTokens.sha256Hex(key));
        box.setKeyCreatedAt(Instant.now());
        return new KeyedBox(boxRepository.save(box).getId(), key);
    }

    private String newDisplayToken(Long boxId) {
        String token = SecretTokens.newToken();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        DisplaySession session = new DisplaySession();
        session.setId(UUID.randomUUID());
        session.setBoxId(boxId);
        session.setTokenHash(SecretTokens.sha256Hex(token));
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(1, ChronoUnit.DAYS));
        sessionRepository.save(session);
        return token;
    }

    private Client connect(String path, String origin) throws Exception {
        Client client = new Client();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (origin != null) {
            headers.setOrigin(origin);
        }
        URI uri = URI.create("ws://localhost:" + port + "/museotekbox" + path);
        client.session = new StandardWebSocketClient().execute(client, headers, uri).get(10, TimeUnit.SECONDS);
        return client;
    }

    private Client box(KeyedBox box) throws Exception {
        Client client = connect("/ws/box", null);
        client.send("{\"v\":1,\"type\":\"hello\",\"seq\":1,\"payload\":{\"key\":\"" + box.key() + "\"}}");
        assertThat(client.next().get("type").asText()).isEqualTo("hello.ok");
        return client;
    }

    private BoxStatus statusOf(Long boxId) {
        return boxRepository.findById(boxId).orElseThrow().getStatus();
    }

    private void awaitStatus(Long boxId, BoxStatus expected) throws InterruptedException {
        for (int i = 0; i < 100 && statusOf(boxId) != expected; i++) {
            Thread.sleep(100);
        }
        assertThat(statusOf(boxId)).isEqualTo(expected);
    }

    @Test
    void aBox_logsInWithItsKey_isOnlineWhileConnected_andOfflineAfter() throws Exception {
        KeyedBox box = newBox();

        Client client = box(box);

        JsonNode presence = client.next();
        assertThat(presence.get("type").asText()).isEqualTo("display.presence");
        assertThat(presence.get("payload").get("connected").asBoolean()).isFalse();
        assertThat(statusOf(box.id())).isEqualTo(BoxStatus.ONLINE);

        client.session.close();
        awaitStatus(box.id(), BoxStatus.OFFLINE);
    }

    @Test
    void aBox_fromAnyOrigin_isLetIn() throws Exception {
        KeyedBox box = newBox();
        Client client = connect("/ws/box", "http://somewhere.example");

        client.send("{\"type\":\"hello\",\"payload\":{\"key\":\"" + box.key() + "\"}}");

        assertThat(client.next().get("type").asText()).isEqualTo("hello.ok");
    }

    @Test
    void theBoxAndItsDisplay_hearAboutEachOther_andAboutSyncing() throws Exception {
        KeyedBox box = newBox();
        Client boxClient = box(box);
        boxClient.next(); // display.presence: no display yet

        Client display = connect("/ws/display", "http://localhost:5173");
        display.send("{\"type\":\"hello\",\"payload\":{\"token\":\"" + newDisplayToken(box.id()) + "\"}}");
        assertThat(display.next().get("type").asText()).isEqualTo("hello.ok");
        JsonNode boxPresence = display.next();
        assertThat(boxPresence.get("type").asText()).isEqualTo("box.presence");
        assertThat(boxPresence.get("payload").get("online").asBoolean()).isTrue();
        JsonNode displayPresence = boxClient.next();
        assertThat(displayPresence.get("payload").get("connected").asBoolean()).isTrue();

        transactionTemplate.executeWithoutResult(status ->
                events.publishEvent(new BoxesNeedSyncEvent(List.of(box.id()))));

        assertThat(boxClient.next().get("type").asText()).isEqualTo("sync.required");
        assertThat(display.next().get("type").asText()).isEqualTo("idle.changed");
    }

    @Test
    void replacingTheKey_closesTheBoxAtOnce() throws Exception {
        KeyedBox box = newBox();
        Client client = box(box);
        client.next(); // display.presence

        transactionTemplate.executeWithoutResult(status -> events.publishEvent(new BoxKeyChangedEvent(box.id())));

        assertThat(client.next().get("type").asText()).isEqualTo("session.revoked");
        assertThat(client.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("SESSION_REVOKED"));
        assertThat(statusOf(box.id())).isEqualTo(BoxStatus.OFFLINE);
    }

    @Test
    void theRelay_stateToTheDisplay_withMediaLinks_reportsToTheBox_andWhatItPlaysIsStored() throws Exception {
        KeyedBox box = newBox();
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setOrgId(UUID.randomUUID());
        project.setName("Art out of the box");
        projectRepository.save(project);
        transactionTemplate.executeWithoutResult(status ->
                boxRepository.findById(box.id()).orElseThrow().getAssignedProjects().add(project));
        Client boxClient = box(box);
        boxClient.next(); // display.presence
        Client display = connect("/ws/display", "http://localhost:5173");
        display.send("{\"type\":\"hello\",\"payload\":{\"token\":\"" + newDisplayToken(box.id()) + "\"}}");
        display.next(); // hello.ok
        display.next(); // box.presence
        boxClient.next(); // display.presence: connected
        UUID video = UUID.randomUUID();

        boxClient.send("{\"v\":1,\"type\":\"state\",\"seq\":2,\"payload\":{\"mode\":\"PLAYING\","
                + "\"session\":{\"id\":\"s1\",\"projectId\":\"" + project.getId() + "\"},"
                + "\"scene\":{\"instance\":1,\"sceneKey\":\"4\",\"blocks\":[{\"blockKey\":\"9\",\"type\":\"VIDEO\","
                + "\"content\":{\"mediaId\":\"" + video + "\"}}]},\"overlay\":null}}");

        JsonNode state = display.next();
        assertThat(state.get("type").asText()).isEqualTo("state");
        assertThat(state.get("payload").get("mediaLinks").get(video.toString()).asText())
                .startsWith("/display/media/" + video + "/content?session=");
        Box stored = boxRepository.findById(box.id()).orElseThrow();
        assertThat(stored.getCurrentSceneKey()).isEqualTo("4");

        display.send("{\"type\":\"scene.shown\",\"payload\":{\"sessionId\":\"s1\",\"instance\":1}}");

        JsonNode shown = boxClient.next();
        assertThat(shown.get("type").asText()).isEqualTo("scene.shown");
        assertThat(shown.get("payload").get("instance").asInt()).isEqualTo(1);
    }

    @Test
    void aWrongKey_isRefused() throws Exception {
        Client client = connect("/ws/box", null);

        client.send("{\"type\":\"hello\",\"payload\":{\"key\":\"" + SecretTokens.newToken() + "\"}}");

        assertThat(client.next().get("payload").get("code").asText()).isEqualTo("BOX_KEY_INVALID");
        assertThat(client.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("BOX_KEY_INVALID"));
    }
}
