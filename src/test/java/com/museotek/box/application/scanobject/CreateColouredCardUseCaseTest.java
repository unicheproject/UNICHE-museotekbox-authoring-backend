package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.CardColour;
import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.domain.scanobject.DuplicateRfidTagException;
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

class CreateColouredCardUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectSupport scanObjectSupport = new ScanObjectSupport(scanObjectRepository, scanObjectTypeRepository);
    private final CreateColouredCardUseCase useCase = new CreateColouredCardUseCase(orgAccessGuard, scanObjectSupport, scanObjectRepository);

    @Test
    void success_savesColouredCardScopedToOrg() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.empty());
        when(scanObjectRepository.save(any(ColouredCard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ColouredCard result = useCase.execute(orgId, "Muse Card", "TAG-1", true, null, CardColour.RED);

        verify(orgAccessGuard).requireAccess(orgId);
        ArgumentCaptor<ColouredCard> captor = ArgumentCaptor.forClass(ColouredCard.class);
        verify(scanObjectRepository).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(orgId);
        assertThat(captor.getValue().getName()).isEqualTo("Muse Card");
        assertThat(captor.getValue().getRfidTag()).isEqualTo("TAG-1");
        assertThat(captor.getValue().isReusable()).isTrue();
        assertThat(captor.getValue().getColour()).isEqualTo(CardColour.RED);
        assertThat(captor.getValue().getScanObjectType()).isNull();
        assertThat(result.getColour()).isEqualTo(CardColour.RED);
    }

    @Test
    void success_resolvesScanObjectType() {
        UUID orgId = UUID.randomUUID();
        ScanObjectType type = new ScanObjectType();
        type.setId(5L);
        when(scanObjectTypeRepository.findByIdAndOrgId(5L, orgId)).thenReturn(Optional.of(type));
        when(scanObjectRepository.save(any(ColouredCard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ColouredCard result = useCase.execute(orgId, "Muse Card", null, false, 5L, CardColour.GREEN);

        assertThat(result.getScanObjectType()).isEqualTo(type);
    }

    @Test
    void duplicateRfidTag_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        ColouredCard other = new ColouredCard();
        other.setId(2L);
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase.execute(orgId, "Muse Card", "TAG-1", true, null, CardColour.RED))
                .isInstanceOf(DuplicateRfidTagException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void unknownScanObjectType_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.findByIdAndOrgId(5L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, "Muse Card", null, true, 5L, CardColour.RED))
                .isInstanceOf(ScanObjectTypeNotFoundException.class);

        verify(scanObjectRepository, never()).save(any());
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, "Muse Card", "TAG-1", true, null, CardColour.RED))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectRepository);
    }
}
