package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.DuplicateRfidTagException;
import com.museotek.box.domain.scanobject.Draft;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
import com.museotek.box.domain.scanobject.ThreeDPrintedObject;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
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

class UpdateThreeDPrintedObjectUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectSupport scanObjectSupport = new ScanObjectSupport(scanObjectRepository, scanObjectTypeRepository);
    private final UpdateThreeDPrintedObjectUseCase useCase = new UpdateThreeDPrintedObjectUseCase(orgAccessGuard, scanObjectSupport, scanObjectRepository);

    private ThreeDPrintedObject existingObject(UUID orgId, Long id, String rfidTag) {
        ThreeDPrintedObject object = new ThreeDPrintedObject();
        object.setId(id);
        object.setOrgId(orgId);
        object.setName("Old name");
        object.setRfidTag(rfidTag);
        object.setModelRef("models/old.glb");
        return object;
    }

    @Test
    void success_updatesFields() {
        UUID orgId = UUID.randomUUID();
        ThreeDPrintedObject object = existingObject(orgId, 1L, "TAG-1");
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(object));
        when(scanObjectRepository.findByRfidTag("TAG-2")).thenReturn(Optional.empty());
        when(scanObjectRepository.save(any(ThreeDPrintedObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ThreeDPrintedObject result = useCase.execute(orgId, 1L, "New name", "TAG-2", true, null, "models/new.glb");

        verify(orgAccessGuard).requireAccess(orgId);
        assertThat(result.getName()).isEqualTo("New name");
        assertThat(result.getRfidTag()).isEqualTo("TAG-2");
        assertThat(result.isReusable()).isTrue();
        assertThat(result.getModelRef()).isEqualTo("models/new.glb");
    }

    @Test
    void unchangedRfidTag_doesNotConflictWithItself() {
        UUID orgId = UUID.randomUUID();
        ThreeDPrintedObject object = existingObject(orgId, 1L, "TAG-1");
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(object));
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.of(object));
        when(scanObjectRepository.save(any(ThreeDPrintedObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ThreeDPrintedObject result = useCase.execute(orgId, 1L, "New name", "TAG-1", false, null, "models/new.glb");

        assertThat(result.getRfidTag()).isEqualTo("TAG-1");
    }

    @Test
    void rfidTagTakenByAnother_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        ThreeDPrintedObject object = existingObject(orgId, 1L, "TAG-1");
        ThreeDPrintedObject other = existingObject(orgId, 2L, "TAG-2");
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(object));
        when(scanObjectRepository.findByRfidTag("TAG-2")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-2", true, null, "models/new.glb"))
                .isInstanceOf(DuplicateRfidTagException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void notFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-1", true, null, "models/new.glb"))
                .isInstanceOf(ScanObjectNotFoundException.class);
    }

    @Test
    void wrongSubtype_treatedAsNotFound() {
        UUID orgId = UUID.randomUUID();
        Draft draft = new Draft();
        draft.setId(1L);
        draft.setOrgId(orgId);
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-1", true, null, "models/new.glb"))
                .isInstanceOf(ScanObjectNotFoundException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-1", true, null, "models/new.glb"))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectRepository);
    }
}
