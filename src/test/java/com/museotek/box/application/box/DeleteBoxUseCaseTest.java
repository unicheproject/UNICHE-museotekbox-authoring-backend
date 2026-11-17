package com.museotek.box.application.box;

import com.museotek.box.application.display.DisplaySessionsRevokedEvent;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteBoxUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final DisplaySessionRepository displaySessionRepository = mock(DisplaySessionRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DeleteBoxUseCase useCase =
            new DeleteBoxUseCase(orgAccessGuard, boxRepository, displaySessionRepository, events);

    @Test
    void success_deletesBoxScopedToOrg() {
        UUID orgId = UUID.randomUUID();
        Box box = box(orgId);
        when(displaySessionRepository.findNotRevokedIds(1L)).thenReturn(List.of());

        useCase.execute(orgId, 1L);

        verify(orgAccessGuard).requireManager(orgId);
        verify(boxRepository).delete(box);
        verifyNoInteractions(events);
    }

    @Test
    void deletesTheDisplaysFirst_andClosesTheOpenOnes() {
        UUID orgId = UUID.randomUUID();
        Box box = box(orgId);
        UUID displayId = UUID.randomUUID();
        when(displaySessionRepository.findNotRevokedIds(1L)).thenReturn(List.of(displayId));

        useCase.execute(orgId, 1L);

        InOrder order = inOrder(displaySessionRepository, boxRepository);
        order.verify(displaySessionRepository).deleteByBoxId(1L);
        order.verify(boxRepository).delete(box);
        verify(events).publishEvent(new DisplaySessionsRevokedEvent(List.of(displayId)));
    }

    @Test
    void boxNotFoundForOrg_throwsWithoutDeleting() {
        UUID orgId = UUID.randomUUID();
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(BoxNotFoundException.class);
        verifyNoInteractions(displaySessionRepository, events);
    }

    @Test
    void notOrgManager_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireManager(orgId)).thenThrow(new OrgManagerRequiredException("not a manager"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(OrgManagerRequiredException.class);

        verifyNoInteractions(boxRepository, displaySessionRepository);
    }

    private Box box(UUID orgId) {
        Box box = new Box();
        box.setId(1L);
        box.setOrgId(orgId);
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        return box;
    }
}
