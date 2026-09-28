package com.museotek.box.application.scanobjecttype;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListScanObjectTypesForOrgQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ListScanObjectTypesForOrgQuery query = new ListScanObjectTypesForOrgQuery(orgAccessGuard, scanObjectTypeRepository);

    @Test
    void success_returnsOrgsTypes() {
        UUID orgId = UUID.randomUUID();
        ScanObjectType type = new ScanObjectType();
        when(scanObjectTypeRepository.findByOrgId(orgId)).thenReturn(List.of(type));

        List<ScanObjectType> result = query.execute(orgId);

        assertThat(result).containsExactly(type);
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(orgId)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectTypeRepository);
    }
}
