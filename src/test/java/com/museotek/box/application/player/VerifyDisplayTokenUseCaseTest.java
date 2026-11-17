package com.museotek.box.application.player;

import com.museotek.box.application.display.DisplaySessionProperties;
import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.domain.display.InvalidDisplayTokenException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerifyDisplayTokenUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final String TOKEN = "the-display-token";

    private final DisplaySessionRepository sessionRepository = mock(DisplaySessionRepository.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final VerifyDisplayTokenUseCase useCase = new VerifyDisplayTokenUseCase(sessionRepository, boxRepository,
            new DisplaySessionProperties(Duration.ofDays(30), Duration.ofHours(1), Duration.ofHours(12)), Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID orgId = UUID.randomUUID();

    @Test
    void validToken_answersTheSessionItsBoxAndOrg_withTheStoredOrgName() {
        DisplaySession session = stored(NOW.plus(Duration.ofDays(5)));
        session.setOrgName("Museum of Art");
        box();

        PlayerDisplayView view = useCase.execute(TOKEN);

        assertThat(view).isEqualTo(new PlayerDisplayView(session.getId(), 12L, orgId, "Museum of Art"));
    }

    @Test
    void firstUse_setsLastSeen_andMovesTheExpiryForward() {
        DisplaySession session = stored(NOW.plus(Duration.ofDays(5)));
        box();

        useCase.execute(TOKEN);

        assertThat(session.getLastSeenAt()).isEqualTo(NOW);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void useWithinTheInterval_writesNothing() {
        DisplaySession session = stored(NOW.plus(Duration.ofDays(5)));
        Instant recently = NOW.minus(Duration.ofMinutes(59));
        session.setLastSeenAt(recently);
        box();

        useCase.execute(TOKEN);

        assertThat(session.getLastSeenAt()).isEqualTo(recently);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(5)));
    }

    @Test
    void useAfterTheInterval_movesBothForward() {
        DisplaySession session = stored(NOW.plus(Duration.ofDays(5)));
        session.setLastSeenAt(NOW.minus(Duration.ofHours(1)));
        box();

        useCase.execute(TOKEN);

        assertThat(session.getLastSeenAt()).isEqualTo(NOW);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void unknownOrRevokedToken_isRefused() {
        when(sessionRepository.findByTokenHashAndRevokedAtIsNull(SecretTokens.sha256Hex(TOKEN)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(TOKEN)).isInstanceOf(InvalidDisplayTokenException.class);
    }

    @Test
    void expiredToken_isRefused_evenAtTheExactMoment() {
        DisplaySession session = stored(NOW);
        box();

        assertThatThrownBy(() -> useCase.execute(TOKEN)).isInstanceOf(InvalidDisplayTokenException.class);
        assertThat(session.getLastSeenAt()).isNull();
    }

    @Test
    void tokenOfADeletedBox_isRefused() {
        stored(NOW.plus(Duration.ofDays(5)));
        when(boxRepository.findById(12L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(TOKEN)).isInstanceOf(InvalidDisplayTokenException.class);
    }

    private DisplaySession stored(Instant expiresAt) {
        DisplaySession session = new DisplaySession();
        session.setId(UUID.randomUUID());
        session.setBoxId(12L);
        session.setTokenHash(SecretTokens.sha256Hex(TOKEN));
        session.setExpiresAt(expiresAt);
        when(sessionRepository.findByTokenHashAndRevokedAtIsNull(SecretTokens.sha256Hex(TOKEN)))
                .thenReturn(Optional.of(session));
        return session;
    }

    private void box() {
        Box box = new Box();
        box.setId(12L);
        box.setOrgId(orgId);
        when(boxRepository.findById(12L)).thenReturn(Optional.of(box));
    }
}
