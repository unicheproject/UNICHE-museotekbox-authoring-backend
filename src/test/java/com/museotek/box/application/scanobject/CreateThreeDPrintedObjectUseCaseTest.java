package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.DuplicateRfidTagException;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.domain.scanobject.ThreeDPrintedObject;
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

class CreateThreeDPrintedObjectUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectSupport scanObjectSupport = new ScanObjectSupport(scanObjectRepository, scanObjectTypeRepository);
    private final CreateThreeDPrintedObjectUseCase useCase = new CreateThreeDPrintedObjectUseCase(orgAccessGuard, scanObjectSupport, scanObjectRepository);

    @Test
    void success_savesThreeDPrintedObjectScopedToOrg() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.empty());
        when(scanObjectRepository.save(any(ThreeDPrintedObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ThreeDPrintedObject result = useCase.execute(orgId, "Muse Statue", "TAG-1", true, null, "models/statue.glb");

        verify(orgAccessGuard).requireAccess(orgId);
        ArgumentCaptor<ThreeDPrintedObject> captor = ArgumentCaptor.forClass(ThreeDPrintedObject.class);
        verify(scanObjectRepository).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(orgId);
        assertThat(captor.getValue().getName()).isEqualTo("Muse Statue");
        assertThat(captor.getValue().getRfidTag()).isEqualTo("TAG-1");
        assertThat(captor.getValue().isReusable()).isTrue();
        assertThat(captor.getValue().getModelRef()).isEqualTo("models/statue.glb");
        assertThat(captor.getValue().getScanObjectType()).isNull();
        assertThat(result.getModelRef()).isEqualTo("models/statue.glb");
    }

    @Test
    void success_resolvesScanObjectType() {
        UUID orgId = UUID.randomUUID();
        ScanObjectType type = new ScanObjectType();
        type.setId(5L);
        when(scanObjectTypeRepository.findByIdAndOrgId(5L, orgId)).thenReturn(Optional.of(type));
        when(scanObjectRepository.save(any(ThreeDPrintedObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ThreeDPrintedObject result = useCase.execute(orgId, "Muse Statue", null, false, 5L, "models/statue.glb");

        assertThat(result.getScanObjectType()).isEqualTo(type);
    }

    @Test
    void duplicateRfidTag_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        ThreeDPrintedObject other = new ThreeDPrintedObject();
        other.setId(2L);
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase.execute(orgId, "Muse Statue", "TAG-1", true, null, "models/statue.glb"))
                .isInstanceOf(DuplicateRfidTagException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void unknownScanObjectType_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.findByIdAndOrgId(5L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, "Muse Statue", null, true, 5L, "models/statue.glb"))
                .isInstanceOf(ScanObjectTypeNotFoundException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, "Muse Statue", "TAG-1", true, null, "models/statue.glb"))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectRepository);
    }
}
