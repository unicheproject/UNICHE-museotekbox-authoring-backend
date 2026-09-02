package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.CardColour;
import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.domain.scanobject.DuplicateRfidTagException;
import com.museotek.box.domain.scanobject.PrintedImage;
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

class UpdateColouredCardUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectSupport scanObjectSupport = new ScanObjectSupport(scanObjectRepository, scanObjectTypeRepository);
    private final UpdateColouredCardUseCase useCase = new UpdateColouredCardUseCase(orgAccessGuard, scanObjectSupport, scanObjectRepository);

    private ColouredCard existingCard(UUID orgId, Long id, String rfidTag) {
        ColouredCard card = new ColouredCard();
        card.setId(id);
        card.setOrgId(orgId);
        card.setName("Old name");
        card.setRfidTag(rfidTag);
        card.setColour(CardColour.RED);
        return card;
    }

    @Test
    void success_updatesFields() {
        UUID orgId = UUID.randomUUID();
        ColouredCard card = existingCard(orgId, 1L, "TAG-1");
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(card));
        when(scanObjectRepository.findByRfidTag("TAG-2")).thenReturn(Optional.empty());
        when(scanObjectRepository.save(any(ColouredCard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ColouredCard result = useCase.execute(orgId, 1L, "New name", "TAG-2", true, null, CardColour.GREEN);

        verify(orgAccessGuard).requireAccess(orgId);
        assertThat(result.getName()).isEqualTo("New name");
        assertThat(result.getRfidTag()).isEqualTo("TAG-2");
        assertThat(result.isReusable()).isTrue();
        assertThat(result.getColour()).isEqualTo(CardColour.GREEN);
    }

    @Test
    void unchangedRfidTag_doesNotConflictWithItself() {
        UUID orgId = UUID.randomUUID();
        ColouredCard card = existingCard(orgId, 1L, "TAG-1");
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(card));
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.of(card));
        when(scanObjectRepository.save(any(ColouredCard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ColouredCard result = useCase.execute(orgId, 1L, "New name", "TAG-1", false, null, CardColour.RED);

        assertThat(result.getRfidTag()).isEqualTo("TAG-1");
    }

    @Test
    void rfidTagTakenByAnother_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        ColouredCard card = existingCard(orgId, 1L, "TAG-1");
        ColouredCard other = existingCard(orgId, 2L, "TAG-2");
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(card));
        when(scanObjectRepository.findByRfidTag("TAG-2")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-2", true, null, CardColour.RED))
                .isInstanceOf(DuplicateRfidTagException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void notFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-1", true, null, CardColour.RED))
                .isInstanceOf(ScanObjectNotFoundException.class);
    }

    @Test
    void wrongSubtype_treatedAsNotFound() {
        UUID orgId = UUID.randomUUID();
        PrintedImage printedImage = new PrintedImage();
        printedImage.setId(1L);
        printedImage.setOrgId(orgId);
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(printedImage));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-1", true, null, CardColour.RED))
                .isInstanceOf(ScanObjectNotFoundException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", "TAG-1", true, null, CardColour.RED))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectRepository);
    }
}
