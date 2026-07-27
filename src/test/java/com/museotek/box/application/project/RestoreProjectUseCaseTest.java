package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RestoreProjectUseCaseTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ProjectCompanionSyncService companionSync = mock(ProjectCompanionSyncService.class);
    private final RestoreProjectUseCase useCase = new RestoreProjectUseCase(catalogueClient, companionSync);

    @Test
    void success_restoresInCatalogueThenUpsertsCompanionRowClearingDeletedAt() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CatalogueProjectDto restored = new CatalogueProjectDto(
                id.toString(), orgId.toString(), "Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"));
        when(catalogueClient.restoreProject(id)).thenReturn(restored);

        CatalogueProjectDto result = useCase.execute(id);

        assertThat(result).isSameAs(restored);
        verify(companionSync).upsert(id, orgId, "Wing");
    }

    @Test
    void catalogueNotFound_neverTouchesLocalCompanionRow() {
        UUID id = UUID.randomUUID();
        when(catalogueClient.restoreProject(id)).thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(CatalogueNotFoundException.class);

        verifyNoInteractions(companionSync);
    }
}
