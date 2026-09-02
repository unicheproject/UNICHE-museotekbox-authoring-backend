package com.museotek.box.application.scanobject;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListScanObjectsForOrgQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final ListScanObjectsForOrgQuery query = new ListScanObjectsForOrgQuery(orgAccessGuard, scanObjectRepository);

    @Test
    void success_returnsScanObjectsForOrg() {
        UUID orgId = UUID.randomUUID();
        ColouredCard card = new ColouredCard();
        when(scanObjectRepository.findByOrgId(orgId)).thenReturn(List.of(card));

        var result = query.execute(orgId);

        assertThat(result).containsExactly(card);
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(orgId)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectRepository);
    }
}
