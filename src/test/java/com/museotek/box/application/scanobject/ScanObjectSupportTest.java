package com.museotek.box.application.scanobject;

import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.domain.scanobject.DuplicateRfidTagException;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScanObjectSupportTest {

    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectSupport support = new ScanObjectSupport(scanObjectRepository, scanObjectTypeRepository);

    private ScanObject existing(Long id) {
        ColouredCard card = new ColouredCard();
        card.setId(id);
        return card;
    }

    @Test
    void ensureRfidTagAvailable_nullTag_isNoOp() {
        support.ensureRfidTagAvailable(null, null);

        verifyNoInteractions(scanObjectRepository);
    }

    @Test
    void ensureRfidTagAvailable_unusedTag_doesNothing() {
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.empty());

        support.ensureRfidTagAvailable("TAG-1", null);
    }

    @Test
    void ensureRfidTagAvailable_takenBySelf_doesNotThrow() {
        ScanObject self = existing(1L);
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.of(self));

        support.ensureRfidTagAvailable("TAG-1", 1L);
    }

    @Test
    void ensureRfidTagAvailable_takenByAnother_throws() {
        ScanObject other = existing(2L);
        when(scanObjectRepository.findByRfidTag("TAG-1")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> support.ensureRfidTagAvailable("TAG-1", 1L))
                .isInstanceOf(DuplicateRfidTagException.class);
    }

    @Test
    void resolveType_nullId_returnsNullWithoutQuerying() {
        ScanObjectType result = support.resolveType(UUID.randomUUID(), null);

        assertThat(result).isNull();
        verifyNoInteractions(scanObjectTypeRepository);
    }

    @Test
    void resolveType_found_returnsIt() {
        UUID orgId = UUID.randomUUID();
        ScanObjectType type = new ScanObjectType();
        type.setId(5L);
        when(scanObjectTypeRepository.findByIdAndOrgId(5L, orgId)).thenReturn(Optional.of(type));

        ScanObjectType result = support.resolveType(orgId, 5L);

        assertThat(result).isEqualTo(type);
    }

    @Test
    void resolveType_notFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(scanObjectTypeRepository.findByIdAndOrgId(5L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> support.resolveType(orgId, 5L))
                .isInstanceOf(ScanObjectTypeNotFoundException.class);
    }
}
