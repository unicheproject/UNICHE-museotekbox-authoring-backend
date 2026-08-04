package com.museotek.box.application.projectaccess;

import com.museotek.box.application.project.ProjectCompanionSyncService;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectAccessGuardTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ProjectCompanionSyncService companionSync = mock(ProjectCompanionSyncService.class);
    private final ProjectAccessGuard guard = new ProjectAccessGuard(catalogueClient, companionSync);

    @Test
    void success_upsertsCompanionRowAndReturnsProject() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CatalogueProjectDto dto = new CatalogueProjectDto(
                id.toString(), orgId.toString(), "Ancient Egypt Wing", "ancient-egypt", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"));
        when(catalogueClient.getProject(id)).thenReturn(dto);

        CatalogueProjectDto result = guard.requireAccess(id);

        assertThat(result).isSameAs(dto);
        verify(companionSync).upsert(id, orgId, "Ancient Egypt Wing");
        verify(companionSync, never()).softDelete(id);
    }

    @Test
    void catalogueNotFound_softDeletesCompanionRowAndRethrows() {
        UUID id = UUID.randomUUID();
        when(catalogueClient.getProject(id)).thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> guard.requireAccess(id)).isInstanceOf(CatalogueNotFoundException.class);

        verify(companionSync).softDelete(id);
        verify(companionSync, never()).upsert(any(), any(), anyString());
    }
}
