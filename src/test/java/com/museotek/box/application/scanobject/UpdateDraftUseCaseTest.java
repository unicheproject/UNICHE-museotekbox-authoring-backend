package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.domain.scanobject.DuplicateRfidTagException;
import com.museotek.box.domain.scanobject.Draft;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
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

class UpdateDraftUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectSupport scanObjectSupport = new ScanObjectSupport(scanObjectRepository, scanObjectTypeRepository);
    private final UpdateDraftUseCase useCase = new UpdateDraftUseCase(orgAccessGuard, scanObjectSupport, scanObjectRepository);

    private Draft existingDraft(UUID orgId, Long id, String rfidTag) {
        Draft draft = new Draft();
        draft.setId(id);
        draft.setOrgId(orgId);
        draft.setName("Old name");
        draft.setRfidTag(rfidTag);
        return draft;
    }

    @Test
    void success_updatesFields() {
        UUID orgId = UUID.randomUUID();
        Draft draft = existingDraft(orgId, 1L, null);
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(draft));
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.empty());
        when(scanObjectRepository.save(any(Draft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Draft result = useCase.execute(orgId, 1L, "New name", "TAG-1", true, null);

        verify(orgAccessGuard).requireAccess(orgId);
        assertThat(result.getName()).isEqualTo("New name");
        assertThat(result.getRfidTag()).isEqualTo("TAG-1");
        assertThat(result.isReusable()).isTrue();
    }

    @Test
    void unchangedRfidTag_doesNotConflictWithItself() {
        UUID orgId = UUID.randomUUID();
        Draft draft = existingDraft(orgId, 1L, "TAG-1");
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(draft));
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.of(draft));
        when(scanObjectRepository.save(any(Draft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Draft result = useCase.execute(orgId, 1L, "New name", "TAG-1", false, null);

        assertThat(result.getRfidTag()).isEqualTo("TAG-1");
    }

    @Test
    void rfidTagTakenByAnother_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        Draft draft = existingDraft(orgId, 1L, "TAG-1");
        Draft other = existingDraft(orgId, 2L, "TAG-2");
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(draft));
        when(scanObjectRepository.findByRfidTag("TAG-2")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-2", true, null))
                .isInstanceOf(DuplicateRfidTagException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void notFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-1", true, null))
                .isInstanceOf(ScanObjectNotFoundException.class);
    }

    @Test
    void wrongSubtype_treatedAsNotFound() {
        UUID orgId = UUID.randomUUID();
        ColouredCard colouredCard = new ColouredCard();
        colouredCard.setId(1L);
        colouredCard.setOrgId(orgId);
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(colouredCard));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-1", true, null))
                .isInstanceOf(ScanObjectNotFoundException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-1", true, null))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectRepository);
    }
}
