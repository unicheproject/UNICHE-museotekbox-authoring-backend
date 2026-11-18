package com.museotek.box.player.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.museotek.box.application.box.BoxKeyChangedEvent;
import com.museotek.box.application.box.BoxesNeedSyncEvent;
import com.museotek.box.application.player.BoxPresenceUseCase;
import com.museotek.box.application.player.PlayerBoxIdentity;
import com.museotek.box.application.player.PlayerDisplayView;
import com.museotek.box.application.player.VerifyBoxKeyUseCase;
import com.museotek.box.application.player.VerifyDisplayTokenUseCase;
import com.museotek.box.domain.box.InvalidBoxKeyException;
import com.museotek.box.player.PlayerSocketProperties;
import com.museotek.box.player.PlayerTimer;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The Box socket's login, its online status, presence between the Box and its display, sync and pings.
class BoxSocketHandlerTest {

    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private static final PlayerBoxIdentity BOX = new PlayerBoxIdentity(7L, UUID.randomUUID());
    private static final PlayerDisplayView DISPLAY = new PlayerDisplayView(UUID.randomUUID(), 7L, BOX.orgId(), "Museum");

    private final MovingClock clock = new MovingClock(Instant.parse("2026-11-17T10:00:00Z"));
    private final VerifyBoxKeyUseCase verifyKey = mock(VerifyBoxKeyUseCase.class);
    private final VerifyDisplayTokenUseCase verifyToken = mock(VerifyDisplayTokenUseCase.class);
    private final BoxPresenceUseCase presence = mock(BoxPresenceUseCase.class);
    private final PlayerTimer timer = mock(PlayerTimer.class);
    private final ScheduledFuture<?> deadline = mock(ScheduledFuture.class);
    private final Connections connections = new Connections(presence);
    private final PlayerSocketProperties properties = LoginLimitsTest.properties(10);

    private final BoxSocketHandler boxes;
    private final DisplaySocketHandler displays;

    BoxSocketHandlerTest() {
        doReturn(deadline).when(timer).schedule(any(), any());
        when(deadline.cancel(false)).thenReturn(true);
        LoginLimits limits = new LoginLimits(clock, properties);
        boxes = new BoxSocketHandler(verifyKey, connections, limits, timer, properties, JSON, clock);
        displays = new DisplaySocketHandler(verifyToken, connections, limits, timer, properties, JSON, clock);
        when(verifyKey.execute(anyString())).thenThrow(new InvalidBoxKeyException("no"));
        doReturn(BOX).when(verifyKey).execute("the-key");
        when(verifyToken.execute("the-token")).thenReturn(DISPLAY);
    }

    private FakeSession box() {
        FakeSession session = new FakeSession();
        boxes.afterConnectionEstablished(session);
        boxes.handleTextMessage(session, new TextMessage("{\"type\":\"hello\",\"payload\":{\"key\":\"the-key\"}}"));
        return session;
    }

    private FakeSession display() {
        FakeSession session = new FakeSession();
        displays.afterConnectionEstablished(session);
        displays.handleTextMessage(session,
                new TextMessage("{\"type\":\"hello\",\"payload\":{\"token\":\"the-token\"}}"));
        return session;
    }

    private static List<String> types(FakeSession session) {
        return session.sentMessages().stream().map(m -> m.get("type").asText()).toList();
    }

    @Test
    void hello_aValidKey_logsIn_marksTheBoxOnline_andSaysWhetherItsDisplayIsThere() {
        FakeSession box = box();

        JsonNode reply = box.sentMessages().get(0);
        assertThat(reply.get("type").asText()).isEqualTo("hello.ok");
        assertThat(reply.get("payload").get("boxId").asLong()).isEqualTo(7L);
        assertThat(reply.get("payload").get("orgId").asText()).isEqualTo(BOX.orgId().toString());
        assertThat(reply.get("payload").has("key")).isFalse();
        JsonNode presenceMessage = box.lastMessage();
        assertThat(presenceMessage.get("type").asText()).isEqualTo("display.presence");
        assertThat(presenceMessage.get("payload").get("connected").asBoolean()).isFalse();
        verify(presence).markOnline(7L);
    }

    @Test
    void hello_aWrongKey_closesTheConnection() {
        FakeSession session = new FakeSession();
        boxes.afterConnectionEstablished(session);

        boxes.handleTextMessage(session, new TextMessage("{\"type\":\"hello\",\"payload\":{\"key\":\"wrong\"}}"));

        assertThat(session.lastMessage().get("payload").get("code").asText()).isEqualTo("BOX_KEY_INVALID");
        assertThat(session.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("BOX_KEY_INVALID"));
        verify(presence, never()).markOnline(any());
    }

    @Test
    void hello_theDisplaysTokenField_isNotTheBoxsKey() {
        FakeSession session = new FakeSession();
        boxes.afterConnectionEstablished(session);

        boxes.handleTextMessage(session, new TextMessage("{\"type\":\"hello\",\"payload\":{\"token\":\"the-key\"}}"));

        assertThat(session.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("MALFORMED_MESSAGE"));
    }

    @Test
    void theBoxAndItsDisplay_hearAboutEachOther() {
        FakeSession display = display();
        assertThat(display.lastMessage().get("type").asText()).isEqualTo("box.presence");
        assertThat(display.lastMessage().get("payload").get("online").asBoolean()).isFalse();

        FakeSession box = box();
        assertThat(box.lastMessage().get("payload").get("connected").asBoolean()).isTrue();
        assertThat(display.lastMessage().get("payload").get("online").asBoolean()).isTrue();

        boxes.afterConnectionClosed(box, CloseStatus.GOING_AWAY);
        assertThat(display.lastMessage().get("type").asText()).isEqualTo("box.presence");
        assertThat(display.lastMessage().get("payload").get("online").asBoolean()).isFalse();
        verify(presence).markOffline(7L);
    }

    @Test
    void theDisplayLeaving_isToldToTheBox() {
        FakeSession box = box();
        FakeSession display = display();
        assertThat(box.lastMessage().get("payload").get("connected").asBoolean()).isTrue();

        displays.afterConnectionClosed(display, CloseStatus.GOING_AWAY);

        assertThat(box.lastMessage().get("type").asText()).isEqualTo("display.presence");
        assertThat(box.lastMessage().get("payload").get("connected").asBoolean()).isFalse();
    }

    @Test
    void theBoxConnectingAgain_replacesTheOldConnection_whichClosingLeavesItOnline() {
        FakeSession first = box();
        FakeSession second = box();

        assertThat(first.lastMessage().get("type").asText()).isEqualTo("session.replaced");
        assertThat(first.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("SESSION_REPLACED"));
        boxes.afterConnectionClosed(first, CloseStatus.POLICY_VIOLATION);

        verify(presence, never()).markOffline(any());
        assertThat(second.isOpen()).isTrue();
    }

    @Test
    void aReplacedOrRemovedKey_closesTheBox_andItGoesOffline() {
        FakeSession display = display();
        FakeSession box = box();

        connections.onBoxKeyChanged(new BoxKeyChangedEvent(7L));

        assertThat(box.lastMessage().get("type").asText()).isEqualTo("session.revoked");
        assertThat(box.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("SESSION_REVOKED"));
        verify(presence).markOffline(7L);
        assertThat(display.lastMessage().get("payload").get("online").asBoolean()).isFalse();
    }

    @Test
    void aChangeToWhatTheBoxPlays_tellsTheBoxToSync_andItsDisplayToReload() {
        FakeSession box = box();
        FakeSession display = display();
        FakeSession otherBox = new FakeSession();

        connections.onBoxesNeedSync(new BoxesNeedSyncEvent(List.of(7L, 8L)));

        assertThat(box.lastMessage().get("type").asText()).isEqualTo("sync.required");
        assertThat(display.lastMessage().get("type").asText()).isEqualTo("idle.changed");
        assertThat(otherBox.sentMessages()).isEmpty();
    }

    @Test
    void afterLogin_otherMessages_areRefusedButTheConnectionStays() {
        FakeSession box = box();

        boxes.handleTextMessage(box, new TextMessage("{\"type\":\"state\",\"payload\":{}}"));

        assertThat(box.lastMessage().get("payload").get("code").asText()).isEqualTo("MESSAGE_NOT_ALLOWED");
        assertThat(box.isOpen()).isTrue();
        assertThat(types(box)).startsWith("hello.ok", "display.presence");
    }

    @Test
    void heartbeat_pingsOpenConnections_andClosesThoseThatStopAnswering() {
        FakeSession answering = box();
        FakeSession silent = display();

        clock.advance(Duration.ofSeconds(25));
        boxes.handlePongMessage(answering, new PongMessage());
        boxes.heartbeat();
        displays.heartbeat();
        assertThat(answering.pings()).isEqualTo(1);
        assertThat(silent.pings()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(40));
        boxes.heartbeat();
        displays.heartbeat();

        assertThat(answering.isOpen()).isTrue();
        assertThat(answering.pings()).isEqualTo(2);
        assertThat(silent.closeStatus()).isEqualTo(CloseStatus.SESSION_NOT_RELIABLE);
    }

    @Test
    void theHeartbeat_isScheduledAtThePingInterval() {
        verify(timer, times(2)).scheduleAtFixedRate(any(), eq(Duration.ofSeconds(25)));
    }

    static final class MovingClock extends Clock {

        private Instant now;

        MovingClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
