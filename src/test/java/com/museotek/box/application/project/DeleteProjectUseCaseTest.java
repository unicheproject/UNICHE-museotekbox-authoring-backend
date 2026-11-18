package com.museotek.box.application.project;

import com.museotek.box.application.box.BoxesNeedSyncEvent;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteProjectUseCaseTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ProjectCompanionSyncService companionSync = mock(ProjectCompanionSyncService.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DeleteProjectUseCase useCase = new DeleteProjectUseCase(catalogueClient, companionSync, boxRepository, events);

    @Test
    void success_deletesInCatalogueThenSoftDeletesCompanionRow() {
        UUID id = UUID.randomUUID();

        useCase.execute(id);

        verify(catalogueClient).deleteProject(id);
        verify(companionSync).softDelete(id);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void catalogueForbidden_neverTouchesLocalCompanionRow() {
        UUID id = UUID.randomUUID();
        doThrow(new CatalogueForbiddenException("not authorised")).when(catalogueClient).deleteProject(id);

        assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(companionSync);
    }

    @Test
    void theBoxesThatHaveIt_areToldToSync() {
        UUID id = UUID.randomUUID();
        when(boxRepository.findIdsByAssignedProject(id)).thenReturn(List.of(4L));

        useCase.execute(id);

        verify(events).publishEvent(new BoxesNeedSyncEvent(List.of(4L)));
    }
}
