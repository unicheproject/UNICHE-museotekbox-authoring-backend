package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeInUseException;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.RuleRepository;
import com.museotek.box.infrastructure.repository.SceneRepository;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteScanObjectTypeUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final SceneRepository sceneRepository = mock(SceneRepository.class);
    private final RuleRepository ruleRepository = mock(RuleRepository.class);
    private final DeleteScanObjectTypeUseCase useCase = new DeleteScanObjectTypeUseCase(
            orgAccessGuard, scanObjectTypeRepository, scanObjectRepository, sceneRepository, ruleRepository);

    private ScanObjectType existingType(UUID orgId) {
        ScanObjectType type = new ScanObjectType();
        type.setId(1L);
        type.setOrgId(orgId);
        return type;
    }

    @Test
    void success_deletesWhenNotReferencedAnywhere() {
        UUID orgId = UUID.randomUUID();
        ScanObjectType type = existingType(orgId);
        when(scanObjectTypeRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(type));
        when(scanObjectRepository.existsByScanObjectTypeId(1L)).thenReturn(false);
        when(sceneRepository.existsByInitCardTypeId(1L)).thenReturn(false);
        when(ruleRepository.existsByScanObjectTypeId(1L)).thenReturn(false);

        useCase.execute(orgId, 1L);

        verify(orgAccessGuard).requireAccess(orgId);
        verify(scanObjectTypeRepository).delete(type);
    }

    @Test
    void referencedByScanObject_throwsWithoutDeleting() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(existingType(orgId)));
        when(scanObjectRepository.existsByScanObjectTypeId(1L)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(ScanObjectTypeInUseException.class);

        verify(scanObjectTypeRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void referencedBySceneInitCardType_throwsWithoutDeleting() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(existingType(orgId)));
        when(scanObjectRepository.existsByScanObjectTypeId(1L)).thenReturn(false);
        when(sceneRepository.existsByInitCardTypeId(1L)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(ScanObjectTypeInUseException.class);

        verify(scanObjectTypeRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void referencedByRule_throwsWithoutDeleting() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(existingType(orgId)));
        when(scanObjectRepository.existsByScanObjectTypeId(1L)).thenReturn(false);
        when(sceneRepository.existsByInitCardTypeId(1L)).thenReturn(false);
        when(ruleRepository.existsByScanObjectTypeId(1L)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(ScanObjectTypeInUseException.class);

        verify(scanObjectTypeRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void typeNotFoundForOrg_throwsWithoutCheckingReferences() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(ScanObjectTypeNotFoundException.class);

        verifyNoInteractions(scanObjectRepository, sceneRepository, ruleRepository);
    }

    @Test
    void orgAccessDenied_neverTouchesRepositories() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectTypeRepository, scanObjectRepository, sceneRepository, ruleRepository);
    }
}
