package com.museotek.box.application.scanobjecttype;

import com.museotek.box.domain.scanobject.ExitCardTypeTakenException;
import com.museotek.box.domain.scanobject.InvalidScanObjectTypeException;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeInUseException;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpecialCardTypesTest {

    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final PublishedExperienceRepository publishedExperienceRepository = mock(PublishedExperienceRepository.class);
    private final SpecialCardTypes specialCardTypes = new SpecialCardTypes(scanObjectTypeRepository, publishedExperienceRepository);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void plainType_passesWithoutLookingAnythingUp() {
        assertThatCode(() -> specialCardTypes.check(type(1L, false, false), false, false)).doesNotThrowAnyException();

        verifyNoInteractions(scanObjectTypeRepository, publishedExperienceRepository);
    }

    @Test
    void universalAndExit_isInvalid() {
        assertThatThrownBy(() -> specialCardTypes.check(type(null, true, true), false, false))
                .isInstanceOf(InvalidScanObjectTypeException.class);
    }

    @Test
    void secondExitInTheOrg_isRefused() {
        when(scanObjectTypeRepository.existsByOrgIdAndExitTrue(orgId)).thenReturn(true);

        assertThatThrownBy(() -> specialCardTypes.check(type(null, false, true), false, false))
                .isInstanceOf(ExitCardTypeTakenException.class);
    }

    @Test
    void becomingExit_whileAPublishedCopyUsesIt_isRefused() {
        when(publishedExperienceRepository.latestCopyUsesCardType(1L)).thenReturn(true);

        assertThatThrownBy(() -> specialCardTypes.check(type(1L, false, true), false, false))
                .isInstanceOf(ScanObjectTypeInUseException.class);
    }

    @Test
    void becomingUniversal_whileItStartsAPublishedCopy_isRefused() {
        when(publishedExperienceRepository.latestCopyStartsWith(1L)).thenReturn(true);

        assertThatThrownBy(() -> specialCardTypes.check(type(1L, true, false), false, false))
                .isInstanceOf(ScanObjectTypeInUseException.class);
    }

    @Test
    void flagsThatDidNotChange_areNotCheckedAgain() {
        assertThatCode(() -> specialCardTypes.check(type(1L, false, true), false, true)).doesNotThrowAnyException();
        assertThatCode(() -> specialCardTypes.check(type(1L, true, false), true, false)).doesNotThrowAnyException();

        verifyNoInteractions(scanObjectTypeRepository, publishedExperienceRepository);
    }

    private ScanObjectType type(Long id, boolean universal, boolean exit) {
        ScanObjectType type = new ScanObjectType();
        type.setId(id);
        type.setOrgId(orgId);
        type.setUniversal(universal);
        type.setExit(exit);
        return type;
    }
}
