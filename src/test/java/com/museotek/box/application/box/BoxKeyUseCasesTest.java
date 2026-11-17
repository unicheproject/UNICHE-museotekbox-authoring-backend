package com.museotek.box.application.box;

import com.museotek.box.application.box.CreateBoxKeyUseCase.CreatedBoxKey;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.application.player.PlayerBoxIdentity;
import com.museotek.box.application.player.VerifyBoxKeyUseCase;
import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.InvalidBoxKeyException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Making, replacing, removing and checking a Box's key.
class BoxKeyUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final CreateBoxKeyUseCase create =
            new CreateBoxKeyUseCase(orgAccessGuard, boxRepository, events, Clock.fixed(NOW, ZoneOffset.UTC));
    private final RemoveBoxKeyUseCase remove = new RemoveBoxKeyUseCase(orgAccessGuard, boxRepository, events);
    private final VerifyBoxKeyUseCase verifyKey = new VerifyBoxKeyUseCase(boxRepository);

    private final UUID orgId = UUID.randomUUID();
    private final Box box = box();

    private Box box() {
        Box box = new Box();
        box.setId(12L);
        box.setOrgId(orgId);
        when(boxRepository.findByIdAndOrgId(12L, orgId)).thenReturn(Optional.of(box));
        return box;
    }

    @Test
    void create_storesOnlyTheHash_andReturnsTheKeyOnce() {
        CreatedBoxKey created = create.execute(orgId, 12L);

        verify(orgAccessGuard).requireManager(orgId);
        assertThat(created.key()).hasSize(43);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(box.getKeyHash()).isEqualTo(SecretTokens.sha256Hex(created.key()));
        assertThat(box.getKeyCreatedAt()).isEqualTo(NOW);
        // The first key replaces nothing, so no connection needs closing.
        verify(events, never()).publishEvent(any());
    }

    @Test
    void create_again_replacesTheOldKey_andSaysSo() {
        String oldHash = SecretTokens.sha256Hex(create.execute(orgId, 12L).key());

        String newKey = create.execute(orgId, 12L).key();

        assertThat(box.getKeyHash()).isEqualTo(SecretTokens.sha256Hex(newKey)).isNotEqualTo(oldHash);
        verify(events).publishEvent(new BoxKeyChangedEvent(12L));
    }

    @Test
    void create_isForManagersOnly() {
        doThrow(new OrgManagerRequiredException("managers only")).when(orgAccessGuard).requireManager(orgId);

        assertThatThrownBy(() -> create.execute(orgId, 12L)).isInstanceOf(OrgManagerRequiredException.class);
        assertThat(box.getKeyHash()).isNull();
    }

    @Test
    void create_forABoxOfAnotherOrg_is404() {
        UUID otherOrg = UUID.randomUUID();
        when(boxRepository.findByIdAndOrgId(12L, otherOrg)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> create.execute(otherOrg, 12L)).isInstanceOf(BoxNotFoundException.class);
    }

    @Test
    void remove_clearsTheKey_andSaysSo() {
        create.execute(orgId, 12L);

        remove.execute(orgId, 12L);

        assertThat(box.getKeyHash()).isNull();
        assertThat(box.getKeyCreatedAt()).isNull();
        verify(events).publishEvent(new BoxKeyChangedEvent(12L));
    }

    @Test
    void remove_withoutAKey_doesNothing() {
        remove.execute(orgId, 12L);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void remove_isForManagersOnly() {
        create.execute(orgId, 12L);
        doThrow(new OrgManagerRequiredException("managers only")).when(orgAccessGuard).requireManager(orgId);

        assertThatThrownBy(() -> remove.execute(orgId, 12L)).isInstanceOf(OrgManagerRequiredException.class);
        assertThat(box.getKeyHash()).isNotNull();
    }

    @Test
    void verify_aKnownKey_answersItsBoxAndOrg_anUnknownOneIsRefused() {
        when(boxRepository.findByKeyHash(anyString())).thenReturn(Optional.empty());
        when(boxRepository.findByKeyHash(SecretTokens.sha256Hex("the-key"))).thenReturn(Optional.of(box));

        assertThat(verifyKey.execute("the-key")).isEqualTo(new PlayerBoxIdentity(12L, orgId));
        assertThatThrownBy(() -> verifyKey.execute("wrong")).isInstanceOf(InvalidBoxKeyException.class);
    }
}
