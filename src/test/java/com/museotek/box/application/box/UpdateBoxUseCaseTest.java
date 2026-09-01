package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.DuplicateSerialNumberException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;

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

class UpdateBoxUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final UpdateBoxUseCase useCase = new UpdateBoxUseCase(orgAccessGuard, boxRepository);

    private Box existingBox(UUID orgId, Long id, String serialNumber) {
        Box box = new Box();
        box.setId(id);
        box.setOrgId(orgId);
        box.setName("Old name");
        box.setSerialNumber(serialNumber);
        return box;
    }

    @Test
    void success_updatesNameAndSerialNumber() {
        UUID orgId = UUID.randomUUID();
        Box box = existingBox(orgId, 1L, "SN-1");
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(boxRepository.findBySerialNumber("SN-2")).thenReturn(Optional.empty());
        when(boxRepository.save(any(Box.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Box result = useCase.execute(orgId, 1L, "New name", "SN-2");

        verify(orgAccessGuard).requireAccess(orgId);
        assertThat(result.getName()).isEqualTo("New name");
        assertThat(result.getSerialNumber()).isEqualTo("SN-2");
    }

    @Test
    void unchangedSerialNumber_doesNotConflictWithItself() {
        UUID orgId = UUID.randomUUID();
        Box box = existingBox(orgId, 1L, "SN-1");
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(boxRepository.findBySerialNumber("SN-1")).thenReturn(Optional.of(box));
        when(boxRepository.save(any(Box.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Box result = useCase.execute(orgId, 1L, "New name", "SN-1");

        assertThat(result.getSerialNumber()).isEqualTo("SN-1");
    }

    @Test
    void serialNumberTakenByAnotherBox_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        Box box = existingBox(orgId, 1L, "SN-1");
        Box otherBox = existingBox(orgId, 2L, "SN-2");
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(boxRepository.findBySerialNumber("SN-2")).thenReturn(Optional.of(otherBox));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "SN-2"))
                .isInstanceOf(DuplicateSerialNumberException.class);

        verify(boxRepository, never()).save(any());
    }

    @Test
    void boxNotFoundForOrg_throws() {
        UUID orgId = UUID.randomUUID();
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "SN-1"))
                .isInstanceOf(BoxNotFoundException.class);
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "SN-1"))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(boxRepository);
    }
}
