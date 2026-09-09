package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListDeletedProjectsForOrgPassthroughQueryTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ListDeletedProjectsForOrgPassthroughQuery query = new ListDeletedProjectsForOrgPassthroughQuery(catalogueClient);

    @Test
    void execute_isAPureDelegateWithNoLocalSideEffects() {
        UUID orgId = UUID.randomUUID();
        CatalogueProjectDto dto = new CatalogueProjectDto(
                UUID.randomUUID().toString(), orgId.toString(), "Wing", "wing", "DELETED",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), Instant.now(), Instant.now());
        when(catalogueClient.listDeletedProjectsForOrg(orgId)).thenReturn(List.of(dto));

        assertThat(query.execute(orgId)).containsExactly(dto);
    }

    @Test
    void catalogueForbidden_propagates() {
        UUID orgId = UUID.randomUUID();
        when(catalogueClient.listDeletedProjectsForOrg(orgId)).thenThrow(new CatalogueForbiddenException("not authorised"));

        assertThatThrownBy(() -> query.execute(orgId)).isInstanceOf(CatalogueForbiddenException.class);
    }
}
