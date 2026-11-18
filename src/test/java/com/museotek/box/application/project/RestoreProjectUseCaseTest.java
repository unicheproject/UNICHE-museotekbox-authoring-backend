package com.museotek.box.application.project;

import com.museotek.box.application.box.BoxesNeedSyncEvent;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RestoreProjectUseCaseTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ProjectCompanionSyncService companionSync = mock(ProjectCompanionSyncService.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RestoreProjectUseCase useCase = new RestoreProjectUseCase(catalogueClient, companionSync, boxRepository, events);

    @Test
    void success_restoresInCatalogueThenUpsertsCompanionRowClearingDeletedAt() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CatalogueProjectDto restored = new CatalogueProjectDto(
                id.toString(), orgId.toString(), "Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), Instant.now(), Instant.now());
        when(catalogueClient.restoreProject(id)).thenReturn(restored);

        CatalogueProjectDto result = useCase.execute(id);

        assertThat(result).isSameAs(restored);
        verify(companionSync).upsert(id, orgId, "Wing");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void catalogueNotFound_neverTouchesLocalCompanionRow() {
        UUID id = UUID.randomUUID();
        when(catalogueClient.restoreProject(id)).thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(CatalogueNotFoundException.class);

        verifyNoInteractions(companionSync);
    }

    @Test
    void theBoxesThatHaveIt_areToldToSync() {
        UUID id = UUID.randomUUID();
        when(catalogueClient.restoreProject(id)).thenReturn(new CatalogueProjectDto(
                id.toString(), UUID.randomUUID().toString(), "Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), Instant.now(), Instant.now()));
        when(boxRepository.findIdsByAssignedProject(id)).thenReturn(List.of(4L));

        useCase.execute(id);

        verify(events).publishEvent(new BoxesNeedSyncEvent(List.of(4L)));
    }
}
