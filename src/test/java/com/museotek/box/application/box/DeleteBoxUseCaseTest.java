package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteBoxUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final DeleteBoxUseCase useCase = new DeleteBoxUseCase(orgAccessGuard, boxRepository);

    @Test
    void success_deletesBoxScopedToOrg() {
        UUID orgId = UUID.randomUUID();
        Box box = new Box();
        box.setId(1L);
        box.setOrgId(orgId);
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));

        useCase.execute(orgId, 1L);

        verify(orgAccessGuard).requireManager(orgId);
        verify(boxRepository).delete(box);
    }

    @Test
    void boxNotFoundForOrg_throwsWithoutDeleting() {
        UUID orgId = UUID.randomUUID();
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(BoxNotFoundException.class);
    }

    @Test
    void notOrgManager_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireManager(orgId)).thenThrow(new OrgManagerRequiredException("not a manager"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(OrgManagerRequiredException.class);

        verifyNoInteractions(boxRepository);
    }
}
