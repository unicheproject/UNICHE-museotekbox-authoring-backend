package com.museotek.box.application.display;

import com.museotek.box.application.display.CreateDisplaySessionUseCase.CreatedDisplaySession;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.domain.display.DisplaySessionNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The CMS side of displays: open, list, revoke.
class DisplaySessionUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final DisplaySessionProperties PROPERTIES =
            new DisplaySessionProperties(Duration.ofDays(30), Duration.ofHours(1));

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final DisplaySessionRepository sessionRepository = mock(DisplaySessionRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final UUID orgId = UUID.randomUUID();
    private final Long boxId = 12L;

    private final CreateDisplaySessionUseCase create =
            new CreateDisplaySessionUseCase(orgAccessGuard, boxRepository, sessionRepository, PROPERTIES, CLOCK);
    private final ListDisplaySessionsQuery list =
            new ListDisplaySessionsQuery(orgAccessGuard, boxRepository, sessionRepository, CLOCK);
    private final RevokeDisplaySessionUseCase revoke =
            new RevokeDisplaySessionUseCase(orgAccessGuard, boxRepository, sessionRepository, events, CLOCK);

    @Test
    void create_storesOnlyTheHashOfTheToken_tiedToTheBox_expiringAfterTheLifetime() {
        boxInOrg();
        when(sessionRepository.save(any(DisplaySession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatedDisplaySession created = create.execute(orgId, boxId);

        verify(orgAccessGuard).requireManager(orgId);
        DisplaySession stored = created.session();
        assertThat(stored.getBoxId()).isEqualTo(boxId);
        assertThat(stored.getTokenHash()).isEqualTo(SecretTokens.sha256Hex(created.token()));
        assertThat(stored.getTokenHash()).doesNotContain(created.token());
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(stored.getLastSeenAt()).isNull();
        assertThat(stored.getRevokedAt()).isNull();
    }

    @Test
    void create_byANonManager_storesNothing() {
        doThrow(new OrgManagerRequiredException("managers only")).when(orgAccessGuard).requireManager(orgId);

        assertThatThrownBy(() -> create.execute(orgId, boxId)).isInstanceOf(OrgManagerRequiredException.class);
        verifyNoInteractions(boxRepository, sessionRepository);
    }

    @Test
    void create_forABoxOfAnotherOrg_is404() {
        when(boxRepository.findByIdAndOrgId(boxId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> create.execute(orgId, boxId)).isInstanceOf(BoxNotFoundException.class);
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void list_isForAnyMember_activeAsOfNow() {
        boxInOrg();

        list.execute(orgId, boxId);

        verify(orgAccessGuard).requireAccess(orgId);
        verify(orgAccessGuard, never()).requireManager(orgId);
        verify(sessionRepository).findActive(boxId, NOW);
    }

    @Test
    void list_forABoxOfAnotherOrg_is404() {
        when(boxRepository.findByIdAndOrgId(boxId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> list.execute(orgId, boxId)).isInstanceOf(BoxNotFoundException.class);
        verifyNoInteractions(sessionRepository);
    }

    @Test
    void revoke_setsRevokedAt_andAnnouncesIt() {
        boxInOrg();
        DisplaySession session = new DisplaySession();
        session.setId(UUID.randomUUID());
        when(sessionRepository.findByIdAndBoxIdAndRevokedAtIsNull(session.getId(), boxId))
                .thenReturn(Optional.of(session));

        revoke.execute(orgId, boxId, session.getId());

        verify(orgAccessGuard).requireManager(orgId);
        assertThat(session.getRevokedAt()).isEqualTo(NOW);
        verify(events).publishEvent(new DisplaySessionsRevokedEvent(List.of(session.getId())));
    }

    @Test
    void revoke_unknownOrAlreadyRevoked_is404_andAnnouncesNothing() {
        boxInOrg();
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findByIdAndBoxIdAndRevokedAtIsNull(sessionId, boxId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> revoke.execute(orgId, boxId, sessionId))
                .isInstanceOf(DisplaySessionNotFoundException.class);
        verifyNoInteractions(events);
    }

    @Test
    void revoke_byANonManager_changesNothing() {
        doThrow(new OrgManagerRequiredException("managers only")).when(orgAccessGuard).requireManager(orgId);

        assertThatThrownBy(() -> revoke.execute(orgId, boxId, UUID.randomUUID()))
                .isInstanceOf(OrgManagerRequiredException.class);
        verifyNoInteractions(sessionRepository, events);
    }

    private void boxInOrg() {
        Box box = new Box();
        box.setId(boxId);
        box.setOrgId(orgId);
        when(boxRepository.findByIdAndOrgId(boxId, orgId)).thenReturn(Optional.of(box));
    }
}
