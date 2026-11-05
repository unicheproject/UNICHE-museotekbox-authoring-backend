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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdateScanObjectTypeUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final SpecialCardTypes specialCardTypes = mock(SpecialCardTypes.class);
    private final UpdateScanObjectTypeUseCase useCase =
            new UpdateScanObjectTypeUseCase(orgAccessGuard, scanObjectTypeRepository, specialCardTypes);

    @Test
    void success_updatesName() {
        UUID orgId = UUID.randomUUID();
        ScanObjectType type = new ScanObjectType();
        type.setId(1L);
        type.setOrgId(orgId);
        type.setName("Old name");
        when(scanObjectTypeRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(type));
        when(scanObjectTypeRepository.save(any(ScanObjectType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScanObjectType result = useCase.execute(orgId, 1L, "New name", null, null);

        assertThat(result.getName()).isEqualTo("New name");
    }

    @Test
    void flags_nullLeavesThem_valueSetsThem_andTheOldValuesAreChecked() {
        UUID orgId = UUID.randomUUID();
        ScanObjectType type = new ScanObjectType();
        type.setId(1L);
        type.setOrgId(orgId);
        type.setUniversal(true);
        when(scanObjectTypeRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(type));
        when(scanObjectTypeRepository.save(any(ScanObjectType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScanObjectType result = useCase.execute(orgId, 1L, "Exit", null, true);

        assertThat(result.isUniversal()).isTrue();
        assertThat(result.isExit()).isTrue();
        verify(specialCardTypes).check(result, true, false);
    }

    @Test
    void typeNotFoundForOrg_throwsWithoutSaving() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", null, null))
                .isInstanceOf(ScanObjectTypeNotFoundException.class);
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, "New name", null, null))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(scanObjectTypeRepository);
    }
}
