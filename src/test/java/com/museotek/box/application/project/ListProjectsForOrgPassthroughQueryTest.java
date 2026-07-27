package com.museotek.box.application.project;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListProjectsForOrgPassthroughQueryTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ListProjectsForOrgPassthroughQuery query = new ListProjectsForOrgPassthroughQuery(catalogueClient);

    @Test
    void execute_isAPureDelegateWithNoLocalSideEffects() {
        UUID orgId = UUID.randomUUID();
        CatalogueProjectDto dto = new CatalogueProjectDto(
                UUID.randomUUID().toString(), orgId.toString(), "Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"));
        when(catalogueClient.listProjectsForOrg(orgId)).thenReturn(List.of(dto));

        List<CatalogueProjectDto> result = query.execute(orgId);

        assertThat(result).containsExactly(dto);
    }
}
