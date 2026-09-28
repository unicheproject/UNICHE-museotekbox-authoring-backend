package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateScanObjectTypeUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final CreateScanObjectTypeUseCase useCase = new CreateScanObjectTypeUseCase(orgAccessGuard, scanObjectTypeRepository);

    @Test
    void success_savesTypeScopedToOrg() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.save(any(ScanObjectType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScanObjectType result = useCase.execute(orgId, "Muses");

        verify(orgAccessGuard).requireAccess(orgId);
        ArgumentCaptor<ScanObjectType> captor = ArgumentCaptor.forClass(ScanObjectType.class);
        verify(scanObjectTypeRepository).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(orgId);
        assertThat(captor.getValue().getName()).isEqualTo("Muses");
        assertThat(result.getName()).isEqualTo("Muses");
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, "Muses"))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectTypeRepository);
    }
}
