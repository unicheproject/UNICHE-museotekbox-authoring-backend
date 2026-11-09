package com.museotek.box.application.publish;

import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublishWarningsTest {

    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final PublishWarnings publishWarnings = new PublishWarnings(scanObjectTypeRepository);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void orgWithoutAnExitCard_isWarned() {
        when(scanObjectTypeRepository.existsByOrgIdAndExitTrue(orgId)).thenReturn(false);

        assertThat(publishWarnings.find(orgId))
                .containsExactly("the organisation has no Exit card, so visitors can't leave a visit before it ends");
    }

    @Test
    void orgWithAnExitCard_hasNoWarnings() {
        when(scanObjectTypeRepository.existsByOrgIdAndExitTrue(orgId)).thenReturn(true);

        assertThat(publishWarnings.find(orgId)).isEmpty();
    }
}
