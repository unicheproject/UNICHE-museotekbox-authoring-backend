package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DeleteProjectUseCaseTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ProjectCompanionSyncService companionSync = mock(ProjectCompanionSyncService.class);
    private final DeleteProjectUseCase useCase = new DeleteProjectUseCase(catalogueClient, companionSync);

    @Test
    void success_deletesInCatalogueThenSoftDeletesCompanionRow() {
        UUID id = UUID.randomUUID();

        useCase.execute(id);

        verify(catalogueClient).deleteProject(id);
        verify(companionSync).softDelete(id);
    }

    @Test
    void catalogueForbidden_neverTouchesLocalCompanionRow() {
        UUID id = UUID.randomUUID();
        doThrow(new CatalogueForbiddenException("not authorised")).when(catalogueClient).deleteProject(id);

        assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(companionSync);
    }
}
