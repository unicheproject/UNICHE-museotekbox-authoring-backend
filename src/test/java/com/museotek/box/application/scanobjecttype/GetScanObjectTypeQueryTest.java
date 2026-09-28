package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetScanObjectTypeQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final GetScanObjectTypeQuery query = new GetScanObjectTypeQuery(orgAccessGuard, scanObjectTypeRepository);

    @Test
    void success_returnsTypeScopedToOrg() {
        UUID orgId = UUID.randomUUID();
        ScanObjectType type = new ScanObjectType();
        when(scanObjectTypeRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(type));

        ScanObjectType result = query.execute(orgId, 1L);

        assertThat(result).isSameAs(type);
    }

    @Test
    void typeNotFoundForOrg_throws() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(orgId, 1L)).isInstanceOf(ScanObjectTypeNotFoundException.class);
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(orgId, 1L)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectTypeRepository);
    }
}
