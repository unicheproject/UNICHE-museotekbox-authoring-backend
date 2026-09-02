package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.DuplicateRfidTagException;
import com.museotek.box.domain.scanobject.Draft;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
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

class CreateDraftUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectSupport scanObjectSupport = new ScanObjectSupport(scanObjectRepository, scanObjectTypeRepository);
    private final CreateDraftUseCase useCase = new CreateDraftUseCase(orgAccessGuard, scanObjectSupport, scanObjectRepository);

    @Test
    void success_savesDraftScopedToOrgWithNoRfidTag() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectRepository.save(any(Draft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Draft result = useCase.execute(orgId, "Muse Draft", null, false, null);

        verify(orgAccessGuard).requireAccess(orgId);
        verifyNoInteractions(scanObjectTypeRepository);
        ArgumentCaptor<Draft> captor = ArgumentCaptor.forClass(Draft.class);
        verify(scanObjectRepository).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(orgId);
        assertThat(captor.getValue().getName()).isEqualTo("Muse Draft");
        assertThat(captor.getValue().getRfidTag()).isNull();
        assertThat(captor.getValue().isReusable()).isFalse();
        assertThat(result.getScanObjectType()).isNull();
    }

    @Test
    void success_resolvesScanObjectType() {
        UUID orgId = UUID.randomUUID();
        ScanObjectType type = new ScanObjectType();
        type.setId(5L);
        when(scanObjectTypeRepository.findByIdAndOrgId(5L, orgId)).thenReturn(Optional.of(type));
        when(scanObjectRepository.save(any(Draft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Draft result = useCase.execute(orgId, "Muse Draft", null, false, 5L);

        assertThat(result.getScanObjectType()).isEqualTo(type);
    }

    @Test
    void duplicateRfidTag_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        Draft other = new Draft();
        other.setId(2L);
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase.execute(orgId, "Muse Draft", "TAG-1", false, null))
                .isInstanceOf(DuplicateRfidTagException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void unknownScanObjectType_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.findByIdAndOrgId(5L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, "Muse Draft", null, false, 5L))
                .isInstanceOf(ScanObjectTypeNotFoundException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, "Muse Draft", null, false, null))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectRepository);
    }
}
