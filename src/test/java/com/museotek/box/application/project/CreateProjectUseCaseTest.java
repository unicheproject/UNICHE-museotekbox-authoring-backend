package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueCreateProjectRequest;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateProjectUseCaseTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ProjectCompanionSyncService companionSync = mock(ProjectCompanionSyncService.class);
    private final CreateProjectUseCase useCase = new CreateProjectUseCase(catalogueClient, companionSync);

    @Test
    void success_createsInCatalogueThenUpsertsCompanionRow() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CatalogueCreateProjectRequest request = new CatalogueCreateProjectRequest("Ancient Egypt Wing", "ancient-egypt", "museotek-box");
        CatalogueProjectDto created = new CatalogueProjectDto(
                projectId.toString(), orgId.toString(), "Ancient Egypt Wing", "ancient-egypt", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), Instant.now(), Instant.now());
        when(catalogueClient.createProject(orgId, request)).thenReturn(created);

        CatalogueProjectDto result = useCase.execute(orgId, request);

        assertThat(result).isSameAs(created);
        verify(companionSync).upsert(projectId, orgId, "Ancient Egypt Wing");
    }

    @Test
    void catalogueForbidden_neverTouchesLocalCompanionRow() {
        UUID orgId = UUID.randomUUID();
        CatalogueCreateProjectRequest request = new CatalogueCreateProjectRequest("Wing", "wing", "museotek-box");
        when(catalogueClient.createProject(orgId, request)).thenThrow(new CatalogueForbiddenException("not a manager"));

        assertThatThrownBy(() -> useCase.execute(orgId, request)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(companionSync);
    }

    @Test
    void localDbFailureAfterCatalogueSucceeds_propagatesLeavingCatalogueAheadOfLocalMirror() {
        // Documents the known consistency gap: Catalogue now has a project the local
        // companion row doesn't — self-heals on the next GetProjectQuery read, not here.
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CatalogueCreateProjectRequest request = new CatalogueCreateProjectRequest("Wing", "wing", "museotek-box");
        CatalogueProjectDto created = new CatalogueProjectDto(
                projectId.toString(), orgId.toString(), "Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), Instant.now(), Instant.now());
        when(catalogueClient.createProject(orgId, request)).thenReturn(created);
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(companionSync).upsert(any(), any(), any());

        assertThatThrownBy(() -> useCase.execute(orgId, request)).hasMessage("db down");

        verify(catalogueClient, never()).deleteProject(any());
    }
}
