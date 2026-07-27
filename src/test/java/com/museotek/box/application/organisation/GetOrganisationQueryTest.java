package com.museotek.box.application.organisation;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueOrganisationDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetOrganisationQueryTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final GetOrganisationQuery query = new GetOrganisationQuery(catalogueClient);

    @Test
    void execute_returnsWhatCatalogueClientReturns() {
        UUID orgId = UUID.randomUUID();
        CatalogueOrganisationDto dto = new CatalogueOrganisationDto(orgId.toString(), "Museum of Antiquities", "moa", "ACTIVE");
        when(catalogueClient.getOrganisation(orgId)).thenReturn(dto);

        assertThat(query.execute(orgId)).isSameAs(dto);
    }

    @Test
    void catalogueNotFound_propagates() {
        UUID orgId = UUID.randomUUID();
        when(catalogueClient.getOrganisation(orgId)).thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> query.execute(orgId)).isInstanceOf(CatalogueNotFoundException.class);
    }
}
