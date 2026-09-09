package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.catalogue.CatalogueUpdateProjectRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdateProjectUseCaseTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ProjectCompanionSyncService companionSync = mock(ProjectCompanionSyncService.class);
    private final UpdateProjectUseCase useCase = new UpdateProjectUseCase(catalogueClient, companionSync);

    @Test
    void success_updatesInCatalogueThenUpsertsCompanionRow() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CatalogueUpdateProjectRequest request = new CatalogueUpdateProjectRequest("Renamed Wing");
        CatalogueProjectDto updated = new CatalogueProjectDto(
                id.toString(), orgId.toString(), "Renamed Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), Instant.now(), Instant.now());
        when(catalogueClient.updateProject(id, request)).thenReturn(updated);

        CatalogueProjectDto result = useCase.execute(id, request);

        assertThat(result).isSameAs(updated);
        verify(companionSync).upsert(id, orgId, "Renamed Wing");
    }

    @Test
    void catalogueNotFound_neverTouchesLocalCompanionRow() {
        UUID id = UUID.randomUUID();
        CatalogueUpdateProjectRequest request = new CatalogueUpdateProjectRequest("Renamed Wing");
        when(catalogueClient.updateProject(id, request)).thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> useCase.execute(id, request)).isInstanceOf(CatalogueNotFoundException.class);

        verifyNoInteractions(companionSync);
    }
}
