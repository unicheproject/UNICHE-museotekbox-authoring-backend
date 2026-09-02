package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetScanObjectQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final GetScanObjectQuery query = new GetScanObjectQuery(orgAccessGuard, scanObjectRepository);

    @Test
    void success_returnsScanObject() {
        UUID orgId = UUID.randomUUID();
        ColouredCard card = new ColouredCard();
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(card));

        var result = query.execute(orgId, 1L);

        assertThat(result).isEqualTo(card);
    }

    @Test
    void notFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(orgId, 1L)).isInstanceOf(ScanObjectNotFoundException.class);
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(orgId, 1L)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectRepository);
    }
}
