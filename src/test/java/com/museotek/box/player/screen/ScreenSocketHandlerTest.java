package com.museotek.box.player.screen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.player.PlayerScreenView;
import com.museotek.box.application.player.RedeemScreenSetupCodeUseCase;
import com.museotek.box.application.player.RedeemScreenSetupCodeUseCase.RedeemedScreen;
import com.museotek.box.application.player.VerifyScreenKeyUseCase;
import com.museotek.box.application.screen.ScreenRevokedEvent;
import com.museotek.box.domain.screen.InvalidScreenKeyException;
import com.museotek.box.domain.screen.SetupCodeNotFoundException;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The screen socket's login: SETUP, AUTH, the limits, the login deadline and closing removed screens.
class ScreenSocketHandlerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
    private static final PlayerScreenView SCREEN = new PlayerScreenView(UUID.randomUUID(), UUID.randomUUID(), "Hall A");

    private final RedeemScreenSetupCodeUseCase redeem = mock(RedeemScreenSetupCodeUseCase.class);
    private final VerifyScreenKeyUseCase verify = mock(VerifyScreenKeyUseCase.class);
    private final PlayerTimer timer = mock(PlayerTimer.class);
    private final ScheduledFuture<?> deadline = mock(ScheduledFuture.class);
    private final ScreenConnections connections = new ScreenConnections();

    private ScreenSocketHandler handler = handler(LoginLimitsTest.properties(100, 10));

    private ScreenSocketHandler handler(PlayerSocketProperties properties) {
        doReturn(deadline).when(timer).schedule(any(), any());
        when(deadline.cancel(false)).thenReturn(true);
        return new ScreenSocketHandler(redeem, verify, new LoginLimits(CLOCK, properties), connections, timer,
                properties, new ObjectMapper());
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

    private static void assertError(JsonNode message, String code) {
        assertThat(message.get("type").asText()).isEqualTo("error");
        assertThat(message.get("code").asText()).isEqualTo(code);
    }

    private static void assertRefused(FakeSession session, String code) {
        assertError(session.lastMessage(), code);
        assertThat(session.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION.withReason(code));
    }

    @Test
    void setup_aValidCode_sendsTheKeyOnce_andLogsTheConnectionIn() {
        when(redeem.execute("K7QF-M2XR")).thenReturn(new RedeemedScreen(SCREEN, "the-key"));
        FakeSession session = open();

        send(session, "{\"type\":\"SETUP\",\"setupCode\":\"K7QF-M2XR\"}");

        JsonNode reply = session.lastMessage();
        assertThat(reply.get("type").asText()).isEqualTo("SETUP_OK");
        assertThat(reply.get("screenKey").asText()).isEqualTo("the-key");
        assertThat(reply.get("screenId").asText()).isEqualTo(SCREEN.screenId().toString());
        assertThat(reply.get("screenName").asText()).isEqualTo("Hall A");
        assertThat(session.isOpen()).isTrue();
    }

    @Test
    void afterLogin_otherMessages_areRefusedButTheConnectionStays() {
        when(verify.execute("the-key")).thenReturn(SCREEN);
        FakeSession session = open();
        send(session, "{\"type\":\"AUTH\",\"screenKey\":\"the-key\"}");

        send(session, "{\"type\":\"PICK_BOX\"}");
        assertError(session.lastMessage(), "MESSAGE_NOT_ALLOWED");
        send(session, "not json");
        assertError(session.lastMessage(), "MALFORMED_MESSAGE");
        send(session, "{\"type\":\"AUTH\",\"screenKey\":\"the-key\"}");
        assertError(session.lastMessage(), "MESSAGE_NOT_ALLOWED");

        assertThat(session.isOpen()).isTrue();
        verify(verify).execute("the-key");
    }

    @Test
    void setup_wrongCodes_areAnsweredUntilTheFifth_whichClosesTheConnection() {
        when(redeem.execute(anyString())).thenThrow(new SetupCodeNotFoundException("no"));
        FakeSession session = open();

        for (int i = 0; i < 4; i++) {
            send(session, "{\"type\":\"SETUP\",\"setupCode\":\"WRONG\"}");
            assertError(session.lastMessage(), "SETUP_CODE_NOT_FOUND");
            assertThat(session.isOpen()).isTrue();
        }
        send(session, "{\"type\":\"SETUP\",\"setupCode\":\"WRONG\"}");

        assertRefused(session, "TOO_MANY_ATTEMPTS");
    }

    @Test
    void setup_whilePaused_isRefusedWithoutLookingTheCodeUp() {
        handler = handler(LoginLimitsTest.properties(1, 10));
        when(redeem.execute(anyString())).thenThrow(new SetupCodeNotFoundException("no"));
        send(open(), "{\"type\":\"SETUP\",\"setupCode\":\"WRONG\"}");

        FakeSession other = open();
        send(other, "{\"type\":\"SETUP\",\"setupCode\":\"K7QF-M2XR\"}");

        assertRefused(other, "SETUP_PAUSED");
        verify(redeem, never()).execute("K7QF-M2XR");
    }

    @Test
    void auth_aValidKey_logsIn() {
        when(verify.execute("the-key")).thenReturn(SCREEN);
        FakeSession session = open();

        send(session, "{\"type\":\"AUTH\",\"screenKey\":\"the-key\"}");

        JsonNode reply = session.lastMessage();
        assertThat(reply.get("type").asText()).isEqualTo("AUTH_OK");
        assertThat(reply.get("screenId").asText()).isEqualTo(SCREEN.screenId().toString());
        assertThat(reply.has("screenKey")).isFalse();
    }

    @Test
    void auth_aWrongKey_leavesTheConnectionOpenForASetupCode() {
        when(verify.execute("old-key")).thenThrow(new InvalidScreenKeyException("no"));
        when(redeem.execute("K7QF-M2XR")).thenReturn(new RedeemedScreen(SCREEN, "new-key"));
        FakeSession session = open();

        send(session, "{\"type\":\"AUTH\",\"screenKey\":\"old-key\"}");
        assertError(session.lastMessage(), "SCREEN_KEY_INVALID");
        send(session, "{\"type\":\"SETUP\",\"setupCode\":\"K7QF-M2XR\"}");

        assertThat(session.lastMessage().get("type").asText()).isEqualTo("SETUP_OK");
    }

    @Test
    void auth_theThirdWrongKey_closesTheConnection() {
        when(verify.execute(anyString())).thenThrow(new InvalidScreenKeyException("no"));
        FakeSession session = open();

        send(session, "{\"type\":\"AUTH\",\"screenKey\":\"a\"}");
        send(session, "{\"type\":\"AUTH\",\"screenKey\":\"b\"}");
        assertThat(session.isOpen()).isTrue();
        send(session, "{\"type\":\"AUTH\",\"screenKey\":\"c\"}");

        assertRefused(session, "TOO_MANY_ATTEMPTS");
    }

    @Test
    void auth_aScreenLoggingInTooOften_isRefused() {
        handler = handler(LoginLimitsTest.properties(100, 1));
        when(verify.execute("the-key")).thenReturn(SCREEN);
        send(open(), "{\"type\":\"AUTH\",\"screenKey\":\"the-key\"}");

        FakeSession again = open();
        send(again, "{\"type\":\"AUTH\",\"screenKey\":\"the-key\"}");

        assertRefused(again, "TOO_MANY_RECONNECTS");
    }

    @Test
    void firstMessage_notSetupOrAuth_closesTheConnection() {
        FakeSession notJson = open();
        send(notJson, "hello");
        assertRefused(notJson, "MALFORMED_MESSAGE");

        FakeSession otherType = open();
        send(otherType, "{\"type\":\"PICK_BOX\"}");
        assertRefused(otherType, "MESSAGE_NOT_ALLOWED");

        FakeSession tooLongKey = open();
        send(tooLongKey, "{\"type\":\"AUTH\",\"screenKey\":\"" + "k".repeat(65) + "\"}");
        assertRefused(tooLongKey, "MALFORMED_MESSAGE");

        FakeSession numberCode = open();
        send(numberCode, "{\"type\":\"SETUP\",\"setupCode\":12345678}");
        assertRefused(numberCode, "MALFORMED_MESSAGE");

        verifyNoInteractions(redeem, verify);
    }

    @Test
    void noLoginInTime_closesTheConnection() {
        FakeSession session = open();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(timer).schedule(task.capture(), org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(10)));

        task.getValue().run();

        assertRefused(session, "LOGIN_TIMEOUT");
    }

    @Test
    void aMessageArrivingAfterTheDeadlineWentOff_isIgnored() {
        FakeSession session = open();
        when(deadline.cancel(false)).thenReturn(false);

        send(session, "{\"type\":\"AUTH\",\"screenKey\":\"the-key\"}");

        verifyNoInteractions(verify);
        assertThat(session.sentMessages()).isEmpty();
    }

    @Test
    void removingTheScreen_closesItsConnections_butNotOthers() {
        PlayerScreenView other = new PlayerScreenView(UUID.randomUUID(), SCREEN.orgId(), "Hall B");
        when(verify.execute("the-key")).thenReturn(SCREEN);
        when(verify.execute("other-key")).thenReturn(other);
        FakeSession removed = open();
        send(removed, "{\"type\":\"AUTH\",\"screenKey\":\"the-key\"}");
        FakeSession kept = open();
        send(kept, "{\"type\":\"AUTH\",\"screenKey\":\"other-key\"}");

        connections.onScreenRevoked(new ScreenRevokedEvent(SCREEN.screenId()));

        assertRefused(removed, "SCREEN_KEY_INVALID");
        assertThat(kept.isOpen()).isTrue();
    }

    @Test
    void aClosedConnection_isForgotten() {
        when(verify.execute("the-key")).thenReturn(SCREEN);
        FakeSession session = open();
        send(session, "{\"type\":\"AUTH\",\"screenKey\":\"the-key\"}");
        int sentBefore = session.sentMessages().size();

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);
        connections.onScreenRevoked(new ScreenRevokedEvent(SCREEN.screenId()));

        assertThat(session.sentMessages()).hasSize(sentBefore);
    }

    @Test
    void errors_carryTheConnectionsId_andTheCallersLogIdIsRestored() {
        when(verify.execute(anyString())).thenThrow(new InvalidScreenKeyException("no"));
        FakeSession first = open();
        FakeSession second = open();
        MDC.put(CorrelationIdFilter.MDC_KEY, "http-request-id");

        send(first, "{\"type\":\"AUTH\",\"screenKey\":\"a\"}");
        send(first, "{\"type\":\"AUTH\",\"screenKey\":\"b\"}");
        send(second, "{\"type\":\"AUTH\",\"screenKey\":\"a\"}");

        List<JsonNode> firstErrors = first.sentMessages();
        String firstId = firstErrors.get(0).get("requestId").asText();
        assertThat(firstErrors.get(1).get("requestId").asText()).isEqualTo(firstId);
        assertThat(second.lastMessage().get("requestId").asText()).isNotEqualTo(firstId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("http-request-id");
    }

    @Test
    void anUnexpectedError_beforeLogin_closesWithServerError() {
        when(verify.execute(anyString())).thenThrow(new IllegalStateException("database down"));
        FakeSession session = open();

        send(session, "{\"type\":\"AUTH\",\"screenKey\":\"a\"}");

        assertError(session.lastMessage(), "INTERNAL_ERROR");
        assertThat(session.lastMessage().get("message").asText()).doesNotContain("database");
        assertThat(session.closeStatus()).isEqualTo(CloseStatus.SERVER_ERROR);
    }
}
