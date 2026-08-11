package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.DuplicateSerialNumberException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateBoxUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final CreateBoxUseCase useCase = new CreateBoxUseCase(orgAccessGuard, boxRepository);

    @Test
    void success_savesBoxScopedToOrg() {
        UUID orgId = UUID.randomUUID();
        when(boxRepository.findBySerialNumber("SN-1")).thenReturn(Optional.empty());
        when(boxRepository.save(any(Box.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Box result = useCase.execute(orgId, "Entrance Box", "SN-1");

        verify(orgAccessGuard).requireAccess(orgId);
        ArgumentCaptor<Box> captor = ArgumentCaptor.forClass(Box.class);
        verify(boxRepository).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(orgId);
        assertThat(captor.getValue().getName()).isEqualTo("Entrance Box");
        assertThat(captor.getValue().getSerialNumber()).isEqualTo("SN-1");
        assertThat(result.getOrgId()).isEqualTo(orgId);
    }

    @Test
    void duplicateSerialNumber_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        when(boxRepository.findBySerialNumber("SN-1")).thenReturn(Optional.of(new Box()));

        assertThatThrownBy(() -> useCase.execute(orgId, "Entrance Box", "SN-1"))
                .isInstanceOf(DuplicateSerialNumberException.class);

        verify(boxRepository, never()).save(any());
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, "Entrance Box", "SN-1"))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(boxRepository);
    }
}
