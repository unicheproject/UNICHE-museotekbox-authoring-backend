package com.museotek.box.application.orgaccess;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueOrganisationDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrgAccessGuardTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final OrgAccessGuard guard = new OrgAccessGuard(catalogueClient);

    @Test
    void success_returnsOrganisationFromCatalogue() {
        UUID orgId = UUID.randomUUID();
        CatalogueOrganisationDto dto = new CatalogueOrganisationDto(orgId.toString(), "Mobics", "mobics", "ACTIVE");
        when(catalogueClient.getOrganisation(orgId)).thenReturn(dto);

        CatalogueOrganisationDto result = guard.requireAccess(orgId);

        assertThat(result).isSameAs(dto);
    }

    @Test
    void catalogueForbidden_propagatesUnchanged() {
        UUID orgId = UUID.randomUUID();
        when(catalogueClient.getOrganisation(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> guard.requireAccess(orgId)).isInstanceOf(CatalogueForbiddenException.class);
    }

    @Test
    void catalogueNotFound_propagatesUnchanged() {
        UUID orgId = UUID.randomUUID();
        when(catalogueClient.getOrganisation(orgId)).thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> guard.requireAccess(orgId)).isInstanceOf(CatalogueNotFoundException.class);
    }
}
