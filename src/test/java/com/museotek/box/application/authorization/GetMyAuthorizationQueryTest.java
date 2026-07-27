package com.museotek.box.application.authorization;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueMeAuthorizationDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetMyAuthorizationQueryTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final GetMyAuthorizationQuery query = new GetMyAuthorizationQuery(catalogueClient);

    @Test
    void execute_returnsWhatCatalogueClientReturns() {
        CatalogueMeAuthorizationDto dto = new CatalogueMeAuthorizationDto(
                "user-1", false, List.of(), List.of(), "etag-1", 60L);
        when(catalogueClient.getMeAuthorization()).thenReturn(dto);

        assertThat(query.execute()).isSameAs(dto);
    }
}
