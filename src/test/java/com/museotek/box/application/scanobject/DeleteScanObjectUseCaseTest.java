package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteScanObjectUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final DeleteScanObjectUseCase useCase = new DeleteScanObjectUseCase(orgAccessGuard, scanObjectRepository);

    @Test
    void success_deletesScanObject() {
        UUID orgId = UUID.randomUUID();
        ColouredCard card = new ColouredCard();
        card.setId(1L);
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(card));

        useCase.execute(orgId, 1L);

        verify(orgAccessGuard).requireAccess(orgId);
        verify(scanObjectRepository).delete(card);
    }

    @Test
    void notFound_throwsWithoutDeleting() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(ScanObjectNotFoundException.class);

        verify(scanObjectRepository, never()).delete(any());
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectRepository);
    }
}
