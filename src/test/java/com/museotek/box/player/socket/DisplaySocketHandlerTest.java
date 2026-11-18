package com.museotek.box.player.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.museotek.box.application.display.DisplaySessionsRevokedEvent;
import com.museotek.box.application.player.BoxPresenceUseCase;
import com.museotek.box.application.player.PlayerDisplayView;
import com.museotek.box.application.player.VerifyDisplayTokenUseCase;
import com.museotek.box.domain.display.InvalidDisplayTokenException;
import com.museotek.box.infrastructure.logging.CorrelationIdFilter;
import com.museotek.box.player.PlayerSocketProperties;
import com.museotek.box.player.PlayerTimer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The display socket: hello, the limits, the login deadline, one display per Box, and closing revoked displays.
class DisplaySocketHandlerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private static final PlayerDisplayView DISPLAY = new PlayerDisplayView(UUID.randomUUID(), 7L, UUID.randomUUID(), "Museum");

    private final VerifyDisplayTokenUseCase verify = mock(VerifyDisplayTokenUseCase.class);
    private final PlayerTimer timer = mock(PlayerTimer.class);
    private final ScheduledFuture<?> deadline = mock(ScheduledFuture.class);
    private final BoxPresenceUseCase presence = mock(BoxPresenceUseCase.class);
    private final Connections connections = new Connections(presence);

    private DisplaySocketHandler handler = handler(LoginLimitsTest.properties(10));

    private DisplaySocketHandler handler(PlayerSocketProperties properties) {
        doReturn(deadline).when(timer).schedule(any(), any());
        when(deadline.cancel(false)).thenReturn(true);
        return new DisplaySocketHandler(verify, connections, new LoginLimits(CLOCK, properties), timer, properties,
                JSON, CLOCK);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private FakeSession open() {
        FakeSession session = new FakeSession();
        handler.afterConnectionEstablished(session);
        return session;
    }

    private void send(FakeSession session, String json) {
        handler.handleTextMessage(session, new TextMessage(json));
    }

    private void hello(FakeSession session, String token) {
        send(session, "{\"v\":1,\"type\":\"hello\",\"seq\":1,\"payload\":{\"token\":\"" + token + "\"}}");
    }

    private static void assertError(JsonNode message, String code) {
        assertThat(message.get("type").asText()).isEqualTo("error");
        assertThat(message.get("payload").get("code").asText()).isEqualTo(code);
    }

    private static void assertRefused(FakeSession session, String code) {
        assertError(session.lastMessage(), code);
        assertThat(session.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason(code));
    }

    @Test
    void hello_aValidToken_logsIn_andAnswersInTheEnvelope() {
        when(verify.execute("the-token")).thenReturn(DISPLAY);
        FakeSession session = open();

        hello(session, "the-token");

        JsonNode reply = session.sentMessages().get(0);
        assertThat(reply.get("v").asInt()).isEqualTo(1);
        assertThat(reply.get("type").asText()).isEqualTo("hello.ok");
        assertThat(reply.get("seq").asLong()).isEqualTo(1);
        assertThat(reply.get("sentAt").asText()).isEqualTo("2026-11-17T10:00:00Z");
        assertThat(reply.get("payload").get("sessionId").asText()).isEqualTo(DISPLAY.sessionId().toString());
        assertThat(reply.get("payload").get("boxId").asLong()).isEqualTo(7L);
        assertThat(reply.get("payload").get("orgId").asText()).isEqualTo(DISPLAY.orgId().toString());
        assertThat(reply.get("payload").has("token")).isFalse();
        assertThat(session.isOpen()).isTrue();
    }

    @Test
    void hello_aWrongToken_closesTheConnection() {
        when(verify.execute(anyString())).thenThrow(new InvalidDisplayTokenException("no"));
        FakeSession session = open();

        hello(session, "wrong");

        assertRefused(session, "DISPLAY_TOKEN_INVALID");
    }

    @Test
    void afterLogin_otherMessages_areRefusedButTheConnectionStays() {
        when(verify.execute("the-token")).thenReturn(DISPLAY);
        FakeSession session = open();
        hello(session, "the-token");

        send(session, "{\"type\":\"video.ended\",\"payload\":{}}");
        assertError(session.lastMessage(), "MESSAGE_NOT_ALLOWED");
        send(session, "not json");
        assertError(session.lastMessage(), "MALFORMED_MESSAGE");
        hello(session, "the-token");
        assertError(session.lastMessage(), "MESSAGE_NOT_ALLOWED");

        assertThat(session.isOpen()).isTrue();
        verify(verify).execute("the-token");
        List<Long> seqs = session.sentMessages().stream().map(m -> m.get("seq").asLong()).toList();
        assertThat(seqs).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void aDisplayLoggingInTooOften_isRefused() {
        handler = handler(LoginLimitsTest.properties(1));
        when(verify.execute("the-token")).thenReturn(DISPLAY);
        hello(open(), "the-token");

        FakeSession again = open();
        hello(again, "the-token");

        assertRefused(again, "TOO_MANY_RECONNECTS");
    }

    @Test
    void firstMessage_notAProperHello_closesTheConnection() {
        FakeSession notJson = open();
        send(notJson, "hello");
        assertRefused(notJson, "MALFORMED_MESSAGE");

        FakeSession otherType = open();
        send(otherType, "{\"type\":\"scene.shown\",\"payload\":{}}");
        assertRefused(otherType, "MESSAGE_NOT_ALLOWED");

        FakeSession tokenAtTopLevel = open();
        send(tokenAtTopLevel, "{\"type\":\"hello\",\"token\":\"the-token\"}");
        assertRefused(tokenAtTopLevel, "MALFORMED_MESSAGE");

        FakeSession tooLongToken = open();
        hello(tooLongToken, "t".repeat(65));
        assertRefused(tooLongToken, "MALFORMED_MESSAGE");

        FakeSession numberToken = open();
        send(numberToken, "{\"type\":\"hello\",\"payload\":{\"token\":12345}}");
        assertRefused(numberToken, "MALFORMED_MESSAGE");

        verifyNoInteractions(verify);
    }

    @Test
    void noHelloInTime_closesTheConnection() {
        FakeSession session = open();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(timer).schedule(task.capture(), eq(Duration.ofSeconds(5)));

        task.getValue().run();

        assertRefused(session, "LOGIN_TIMEOUT");
    }

    @Test
    void aMessageArrivingAfterTheDeadlineWentOff_isIgnored() {
        FakeSession session = open();
        when(deadline.cancel(false)).thenReturn(false);

        hello(session, "the-token");

        verifyNoInteractions(verify);
        assertThat(session.sentMessages()).isEmpty();
    }

    @Test
    void aSecondDisplayForTheSameBox_replacesTheFirst() {
        PlayerDisplayView newer = new PlayerDisplayView(UUID.randomUUID(), DISPLAY.boxId(), DISPLAY.orgId(), "Museum");
        when(verify.execute("first")).thenReturn(DISPLAY);
        when(verify.execute("second")).thenReturn(newer);
        FakeSession first = open();
        hello(first, "first");

        FakeSession second = open();
        hello(second, "second");

        assertThat(first.lastMessage().get("type").asText()).isEqualTo("session.replaced");
        assertThat(first.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("SESSION_REPLACED"));
        assertThat(second.sentMessages().get(0).get("type").asText()).isEqualTo("hello.ok");
        assertThat(second.isOpen()).isTrue();
    }

    @Test
    void theReplacedDisplayClosing_doesNotForgetTheNewOne() {
        PlayerDisplayView newer = new PlayerDisplayView(UUID.randomUUID(), DISPLAY.boxId(), DISPLAY.orgId(), "Museum");
        when(verify.execute("first")).thenReturn(DISPLAY);
        when(verify.execute("second")).thenReturn(newer);
        FakeSession first = open();
        hello(first, "first");
        FakeSession second = open();
        hello(second, "second");

        handler.afterConnectionClosed(first, CloseStatus.POLICY_VIOLATION);
        connections.onDisplaySessionsRevoked(new DisplaySessionsRevokedEvent(List.of(newer.sessionId())));

        assertThat(second.lastMessage().get("type").asText()).isEqualTo("session.revoked");
    }

    @Test
    void displaysOfOtherBoxes_areNotReplaced() {
        PlayerDisplayView otherBox = new PlayerDisplayView(UUID.randomUUID(), 8L, DISPLAY.orgId(), "Museum");
        when(verify.execute("first")).thenReturn(DISPLAY);
        when(verify.execute("other")).thenReturn(otherBox);
        FakeSession first = open();
        hello(first, "first");

        hello(open(), "other");

        assertThat(first.isOpen()).isTrue();
    }

    @Test
    void revokingADisplay_closesItsConnection_butNotOthers() {
        PlayerDisplayView otherBox = new PlayerDisplayView(UUID.randomUUID(), 8L, DISPLAY.orgId(), "Museum");
        when(verify.execute("the-token")).thenReturn(DISPLAY);
        when(verify.execute("other")).thenReturn(otherBox);
        FakeSession revoked = open();
        hello(revoked, "the-token");
        FakeSession kept = open();
        hello(kept, "other");

        connections.onDisplaySessionsRevoked(new DisplaySessionsRevokedEvent(List.of(DISPLAY.sessionId())));

        assertThat(revoked.lastMessage().get("type").asText()).isEqualTo("session.revoked");
        assertThat(revoked.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason("SESSION_REVOKED"));
        assertThat(kept.isOpen()).isTrue();
    }

    @Test
    void aClosedConnection_isForgotten() {
        when(verify.execute("the-token")).thenReturn(DISPLAY);
        FakeSession session = open();
        hello(session, "the-token");
        int sentBefore = session.sentMessages().size();

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);
        connections.onDisplaySessionsRevoked(new DisplaySessionsRevokedEvent(List.of(DISPLAY.sessionId())));

        assertThat(session.sentMessages()).hasSize(sentBefore);
    }

    @Test
    void errors_carryTheConnectionsId_andTheCallersLogIdIsRestored() {
        when(verify.execute("the-token")).thenReturn(DISPLAY);
        FakeSession first = open();
        hello(first, "the-token");
        FakeSession second = open();
        MDC.put(CorrelationIdFilter.MDC_KEY, "http-request-id");

        send(first, "{\"type\":\"x\"}");
        send(first, "{\"type\":\"y\"}");
        send(second, "not json");

        List<JsonNode> firstMessages = first.sentMessages();
        String firstId = firstMessages.get(2).get("payload").get("requestId").asText();
        assertThat(firstMessages.get(3).get("payload").get("requestId").asText()).isEqualTo(firstId);
        assertThat(second.lastMessage().get("payload").get("requestId").asText()).isNotEqualTo(firstId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("http-request-id");
    }

    @Test
    void anUnexpectedError_beforeLogin_closesWithServerError() {
        when(verify.execute(anyString())).thenThrow(new IllegalStateException("database down"));
        FakeSession session = open();

        hello(session, "the-token");

        assertError(session.lastMessage(), "INTERNAL_ERROR");
        assertThat(session.lastMessage().get("payload").get("message").asText()).doesNotContain("database");
        assertThat(session.closeStatus()).isEqualTo(CloseStatus.SERVER_ERROR);
    }
}
